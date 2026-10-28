package com.github.k7t3.tcv.app.image;

import com.sun.net.httpserver.HttpServer;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class ImageLoaderTest {

    @Test
    void closingDuringSubmissionCancelsTheImageBeforeItCanBeReturned(FxRobot robot) throws Exception {
        var owner = new AtomicReference<ImageLoader>();
        var queue = new LinkedBlockingQueue<Runnable>();
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var image = new AtomicReference<LazyImage>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(task -> {
            owner.get().close();
            queue.add(task);
        })) {
            owner.set(images);
            robot.interact(() -> {
                image.set(images.decode(() -> {
                    opens.incrementAndGet();
                    throw new AssertionError("closed loader opened an image stream");
                }, 2, 2));

                assertThat(image.get().completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
            });
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            assertThat(opens).hasValue(0);
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
        }
    }

    @Test
    void closedLoaderRejectsNewWorkBeforeSchedulingIt(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        try (var images = new ImageLoader(queue::add)) {
            images.close();

            robot.interact(() -> assertThatThrownBy(() -> images.load("http://127.0.0.1/image", 2, 2))
                    .isInstanceOf(IllegalStateException.class).hasMessage("image loader is closed"));
            assertThat(queue).isEmpty();
        }
    }

    @Test
    void closeDiscardsImagesWaitingForFxDelivery(FxRobot robot) throws Exception {
        var server = startImageServer();
        var queue = new LinkedBlockingQueue<Runnable>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(queue::add)) {
            var image = new AtomicReference<LazyImage>();
            robot.interact(() -> {
                image.set(images.load("http://127.0.0.1:" + server.getAddress().getPort() + "/image", 2, 2));
                try {
                    worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

                    worker.submit(images::close).get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });

            robot.interact(() -> {
                assertThat(image.get().getValue()).isNull();
                assertThat(image.get().completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
            });
            assertThat(worker.submit(() -> 42).get(5, TimeUnit.SECONDS)).isEqualTo(42);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void downloadsAndDecodesTheImageBeforePublishingItOnFx(FxRobot robot) throws Exception {
        var server = startImageServer();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(worker)) {
            var image = new AtomicReference<LazyImage>();
            robot.interact(() -> image.set(images.load(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/image", 2, 2)));

            var decoded = image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                assertThat(image.get().getValue()).isSameAs(decoded);
                assertThat(decoded.isError()).isFalse();
                assertThat(decoded.getWidth()).isEqualTo(2);
                assertThat(decoded.getPixelReader().getColor(0, 0)).isEqualTo(Color.RED);
            });
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startImageServer() throws Exception {
        var pixels = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xffff0000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(pixels, "png", bytes);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, bytes.size());
                exchange.getResponseBody().write(bytes.toByteArray());
            }
        });
        server.start();
        return server;
    }
}
