package com.github.k7t3.tcv.app.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HTTP クライアントを所有し、画像のバイト列を取得する。画像のデコードと UI の反映は呼び出し側が行う。 */
final class ImageDownloader implements AutoCloseable {
    private final Object lifecycle = new Object();
    private HttpClient client;
    private boolean closed;
    private final Duration timeout;
    private final int maxBytes;

    ImageDownloader(Duration timeout) {
        this(timeout, 16 * 1024 * 1024);
    }

    ImageDownloader(Duration timeout, int maxBytes) {
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        this.maxBytes = maxBytes;
    }

    private HttpClient client() throws IOException {
        synchronized (lifecycle) {
            if (closed) throw new IOException("image downloader is closed");
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
            }
            return client;
        }
    }

    byte[] download(URI uri) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        var response = client().sendAsync(request, ignored -> new LimitedBodySubscriber(maxBytes));
        try {
            var received = response.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (received.statusCode() < 200 || received.statusCode() >= 300) {
                throw new IOException("image request failed: HTTP " + received.statusCode());
            }
            return received.body();
        } catch (TimeoutException failure) {
            var timeout = new HttpTimeoutException("image download timed out");
            timeout.initCause(failure);
            throw timeout;
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("image download failed", failure.getCause());
        } finally {
            response.cancel(true);
        }
    }

    @Override
    public void close() {
        final HttpClient owned;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            owned = client;
        }
        if (owned != null) {
            owned.shutdownNow();
            owned.close();
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > maxBytes - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("image exceeds " + maxBytes + " bytes"));
                    return;
                }
                var chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            if (!result.isDone()) result.complete(bytes.toByteArray());
        }
    }
}
