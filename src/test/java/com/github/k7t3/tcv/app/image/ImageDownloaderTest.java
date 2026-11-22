package com.github.k7t3.tcv.app.image;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ImageDownloaderTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationClosesTheHttpConnectionAtThePeer(boolean shutdown) throws Exception {
        var bodyStarted = new CountDownLatch(1);
        var peerClosed = new CompletableFuture<Boolean>();
        var active = new AtomicReference<Socket>();
        var server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        var downloader = new ImageDownloader(shutdown ? Duration.ofSeconds(30) : Duration.ofMillis(500));
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            worker.submit(() -> {
                try (var socket = server.accept()) {
                    active.set(socket);
                    socket.setSoTimeout(5000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String header;
                    do {
                        header = reader.readLine();
                        if (header == null) throw new IOException("request ended before its headers");
                    } while (!header.isEmpty());
                    var response = "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(1);
                    socket.getOutputStream().flush();
                    bodyStarted.countDown();
                    try {
                        peerClosed.complete(reader.read() == -1);
                    } catch (SocketException reset) {
                        peerClosed.complete(true);
                    }
                } catch (Exception failure) {
                    peerClosed.completeExceptionally(failure);
                }
            });
            var downloading = worker.submit(() -> downloader.download(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/image")));
            try {
                assertThat(bodyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                if (shutdown) {
                    assertThatCode(() -> worker.submit(downloader::close).get(3, TimeUnit.SECONDS)).doesNotThrowAnyException();
                    assertThatThrownBy(() -> downloading.get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
                } else {
                    assertThatThrownBy(() -> downloading.get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(HttpTimeoutException.class);
                }

                assertThat(peerClosed.get(3, TimeUnit.SECONDS)).isTrue();
            } finally {
                downloader.close();
                var socket = active.get();
                if (socket != null) socket.close();
                server.close();
            }
        } finally {
            downloader.close();
            server.close();
        }
    }

    @Test
    void closingBeforeTheFirstDownloadPreventsLaterRequests() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(1);
            }
        });
        server.start();
        try (var downloader = new ImageDownloader(Duration.ofSeconds(2))) {
            downloader.close();
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image");

            assertThatThrownBy(() -> downloader.download(uri)).isInstanceOf(IOException.class);
            assertThat(requests.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseLimitAllowsTheBoundaryAndRejectsAdditionalBytes(boolean chunked) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var boundary = new byte[] {1, 2, 3, 4};
        server.createContext("/boundary", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, chunked ? 0 : boundary.length);
                exchange.getResponseBody().write(boundary);
            }
        });
        server.createContext("/overflow", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, chunked ? 0 : 5);
                exchange.getResponseBody().write(new byte[] {1, 2, 3, 4, 5});
            }
        });
        server.start();
        try (var downloader = new ImageDownloader(Duration.ofSeconds(2), 4)) {
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(downloader.download(URI.create(base + "/boundary"))).containsExactly(boundary);

            assertThatThrownBy(() -> downloader.download(URI.create(base + "/overflow")))
                    .isInstanceOf(IOException.class).hasMessage("image exceeds 4 bytes");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void followsARedirectToTheImageWithoutReturningTheRedirectBody() throws Exception {
        var expected = new byte[] {7, 8, 9};
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().add("Location", "/image");
                exchange.sendResponseHeaders(302, -1);
            }
        });
        server.createContext("/image", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, expected.length);
                exchange.getResponseBody().write(expected);
            }
        });
        server.start();
        try (var downloader = new ImageDownloader(Duration.ofSeconds(2))) {
            var bytes = downloader.download(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"));

            assertThat(bytes).containsExactly(expected);
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void unsuccessfulStatusCannotBeUsedAsImageData(int status) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(status, 1);
                exchange.getResponseBody().write(1);
            }
        });
        server.start();
        try (var downloader = new ImageDownloader(Duration.ofSeconds(2))) {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image");

            assertThatThrownBy(() -> downloader.download(uri)).isInstanceOf(IOException.class)
                    .hasMessage("image request failed: HTTP " + status);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void closeCancelsThePendingResponseBeforeItsDeadline() throws Exception {
        var downloader = new ImageDownloader(Duration.ofSeconds(30));
        var server = new IncompleteImageServer();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var downloading = worker.submit(() -> downloader.download(server.uri()));
            try {
                assertThat(server.bodyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = worker.submit(downloader::close);
                try {
                    assertThatCode(() -> closing.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException();
                    assertThatThrownBy(() -> downloading.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
                } finally {
                    server.close();
                    closing.get(5, TimeUnit.SECONDS);
                }
            } finally {
                server.close();
            }
        } finally {
            server.close();
            downloader.close();
        }
    }

    @Test
    void timeoutIncludesAnIncompleteResponseBody() throws Exception {
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
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("server was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        });
        server.start();
        var downloader = new ImageDownloader(Duration.ofMillis(500));
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var downloading = worker.submit(() -> downloader.download(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image")));
            try {
                assertThat(bodyStarted.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> downloading.get(3, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(HttpTimeoutException.class);
            } finally {
                release.countDown();
                server.stop(0);
            }
        } finally {
            downloader.close();
            server.stop(0);
        }
    }

    @Test
    void downloadsTheCompleteResponseBody() throws Exception {
        var expected = new byte[] {1, 2, 3, 4};
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, expected.length);
                exchange.getResponseBody().write(expected);
            }
        });
        server.start();
        try (var downloader = new ImageDownloader(Duration.ofSeconds(2))) {
            var bytes = downloader.download(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image"));

            assertThat(bytes).containsExactly(expected);
        } finally {
            server.stop(0);
        }
    }

    private static final class IncompleteImageServer implements AutoCloseable {
        private final CountDownLatch bodyStarted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final HttpServer server;

        private IncompleteImageServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image");
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }
}
