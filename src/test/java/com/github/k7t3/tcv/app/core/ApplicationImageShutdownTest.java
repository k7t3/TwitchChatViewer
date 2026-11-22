package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@ExtendWith(ApplicationExtension.class)
class ApplicationImageShutdownTest {

    @Test
    void shutdownCancelsImageIoBeforeDrainingTheWorkerWithoutWaitingForFx(FxRobot robot) throws Exception {
        var bodyStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write(1);
                exchange.getResponseBody().flush();
                bodyStarted.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("server was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        });
        server.start();
        var worker = new TaskWorker();
        var helper = new AppHelper(worker, mock(EventPublishers.class), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var loader = new AtomicReference<ImageLoader>();
        var image = new AtomicReference<LazyImage>();
        try (var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                loader.set(helper.getImageLoader());
                image.set(loader.get().load("http://127.0.0.1:" + server.getAddress().getPort() + "/image", 2, 2));
            });
            try {
                assertThat(bodyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = closer.submit(helper::close);
                try {
                    robot.interact(() -> assertThatCode(() -> closing.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException());

                    assertThat(image.get().completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
                    robot.interact(() -> assertThat(image.get().getValue()).isNull());
                } finally {
                    release.countDown();
                    server.stop(0);
                    closing.get(5, TimeUnit.SECONDS);
                }
            } finally {
                release.countDown();
                server.stop(0);
            }
        } finally {
            release.countDown();
            server.stop(0);
            if (loader.get() != null) loader.get().close();
            worker.close();
        }
    }
}
