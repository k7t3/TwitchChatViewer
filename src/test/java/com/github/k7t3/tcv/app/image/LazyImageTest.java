package com.github.k7t3.tcv.app.image;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class LazyImageTest {
    private ExecutorService defaultWorker;

    @BeforeEach
    void createWorker() { defaultWorker = Executors.newVirtualThreadPerTaskExecutor(); }

    @AfterEach
    void closeWorker() { defaultWorker.close(); }

    @Test
    void failedDecodePreservesItsCauseWhenStreamCloseAlsoFails(FxRobot robot) {
        var closeFailure = new IOException("could not close image stream");
        var closes = new AtomicInteger();
        var stream = new ByteArrayInputStream(new byte[] {1, 2, 3}) {
            @Override
            public void close() throws IOException {
                closes.incrementAndGet();
                throw closeFailure;
            }
        };
        var image = new AtomicReference<LazyImage>();
        robot.interact(() -> image.set(new LazyImage(() -> stream, 2, 2, defaultWorker)));
        try {
            assertThatThrownBy(() -> image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).satisfies(failure -> {
                        assertThat(failure.getCause()).isNotSameAs(closeFailure);
                        assertThat(failure.getCause().getSuppressed()).containsExactly(closeFailure);
                    });
            assertThat(closes.get()).isEqualTo(1);
        } finally {
            image.get().close();
        }
    }

    @Test
    void rejectedSubmissionIsReportedThroughCompletion(FxRobot robot) {
        var rejected = new RejectedExecutionException("image worker is closed");
        var image = new AtomicReference<LazyImage>();
        robot.interact(() -> image.set(new LazyImage(() -> {
            throw new AssertionError("rejected provider must not run");
        }, 2, 2, task -> { throw rejected; })));
        try {
            assertThatThrownBy(() -> image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCause(rejected);
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
        } finally {
            image.get().close();
        }
    }

    @Test
    void closingQueuedImageCancelsItWithoutOpeningAStream(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var calls = new AtomicInteger();
        var image = new AtomicReference<LazyImage>();
        robot.interact(() -> {
            image.set(new LazyImage(() -> {
                calls.incrementAndGet();
                return new ByteArrayInputStream(png());
            }, 2, 2, queue::add));

            image.get().close();
            image.get().close();
        });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var task = queue.poll(5, TimeUnit.SECONDS);
            assertThat(task).isNotNull();
            worker.submit(task).get(5, TimeUnit.SECONDS);

            assertThat(calls.get()).isZero();
            assertThat(image.get().completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
        } finally {
            image.get().close();
        }
    }

    @Test
    void corruptImageDataFailsAfterClosingTheStream(FxRobot robot) throws Exception {
        var stream = new TrackingStream(new byte[] {1, 2, 3});
        var image = new AtomicReference<LazyImage>();
        robot.interact(() -> image.set(new LazyImage(() -> stream, 2, 2, defaultWorker)));
        try {
            assertThatThrownBy(() -> image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(Exception.class);
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
            assertThat(stream.closes.get()).isEqualTo(1);
        } finally {
            image.get().close();
        }
    }

    @Test
    void providerFailureIsObservableWithoutPublishingAnImage(FxRobot robot) throws Exception {
        var failure = new IOException("image stream is unavailable");
        var image = new AtomicReference<LazyImage>();
        robot.interact(() -> image.set(new LazyImage(() -> { throw failure; }, 2, 2, defaultWorker)));
        try {
            assertThatThrownBy(() -> image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCause(failure);
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
        } finally {
            image.get().close();
        }
    }

    @Test
    void closePreventsAnImageWaitingForUiDeliveryFromBeingPublished(FxRobot robot) throws Exception {
        var stream = new TrackingStream(png());
        var queue = new LinkedBlockingQueue<Runnable>();
        var image = new AtomicReference<LazyImage>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                image.set(new LazyImage(() -> stream, 2, 2, queue::add));
                try {
                    worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                    assertThat(stream.closes.get()).isEqualTo(1);

                    image.get().close();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });

            robot.interact(() -> assertThat(image.get().getValue()).isNull());
        } finally {
            image.get().close();
        }
    }

    @Test
    void decodingClosesTheProvidedStreamBeforePublishingItsImage(FxRobot robot) throws Exception {
        var stream = new TrackingStream(png());
        var delivered = new CountDownLatch(1);
        var result = new AtomicReference<Image>();
        var lazyImage = new AtomicReference<LazyImage>();
        var closedWhenDelivered = new AtomicInteger();
        robot.interact(() -> {
            var image = new LazyImage(() -> stream, 2, 2, defaultWorker);
            lazyImage.set(image);
            image.valueProperty().addListener((observable, old, value) -> {
                result.set(value);
                closedWhenDelivered.set(stream.closes.get());
                delivered.countDown();
            });
        });

        try {
            assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lazyImage.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS)).isSameAs(result.get());
            robot.interact(() -> {
                assertThat(result.get().isError()).isFalse();
                assertThat(result.get().getWidth()).isEqualTo(2);
                assertThat(result.get().getPixelReader().getColor(0, 0)).isEqualTo(Color.RED);
                assertThat(closedWhenDelivered.get()).isEqualTo(1);
            });
        } finally {
            lazyImage.get().close();
        }
    }

    private static byte[] png() throws Exception {
        var pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xffff0000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(pixels, "png", bytes);
        return bytes.toByteArray();
    }

    private static final class TrackingStream extends ByteArrayInputStream {
        private final AtomicInteger closes = new AtomicInteger();

        private TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
