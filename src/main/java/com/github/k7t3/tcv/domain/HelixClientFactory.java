package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.common.config.Twitch4JGlobal;
import com.github.twitch4j.common.util.TypeConvert;
import com.github.twitch4j.helix.TwitchHelixErrorDecoder;
import com.github.twitch4j.helix.domain.CustomReward;
import com.github.twitch4j.helix.interceptor.*;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import feign.RetryableException;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** SDK の公開 codec / rate limit を使い、再試行は TwitchRequestExecutor が所有する。 */
final class HelixClientFactory {
    static final String OFFICIAL_BASE_URL = "https://api.twitch.tv/helix";
    record Options(String clientId, String clientSecret, String baseUrl, int timeoutMillis) {
        Options {
            Objects.requireNonNull(baseUrl);
            if (timeoutMillis <= 0) throw new IllegalArgumentException("HTTP timeout must be positive");
        }
    }

    static final class Handle implements AutoCloseable {
        private final HelixClient client;
        private final okhttp3.OkHttpClient network;
        private final Lifetime lifetime;

        Handle(HelixClient client, okhttp3.OkHttpClient network, Lifetime lifetime) {
            this.client = client; this.network = network; this.lifetime = lifetime;
        }
        HelixClient client() { return client; }
        boolean isClosed() { return lifetime.isClosed(); }
        @Override public void close() { release(network, lifetime); }
        /** 自身の処理からの停止要求後も、外部の所有者は実終了を待てる。 */
        void awaitClosed() { lifetime.awaitDrained(); }
        boolean isProcessingOnCurrentThread() { return lifetime.isProcessingOnCurrentThread(); }
    }

    static Handle create(CredentialManager credentials, OAuth2Credential token,
                         ScheduledThreadPoolExecutor scheduler, Options options) {
        return create(credentials, token, scheduler, options, Thread::start);
    }

    /** 未開始の要求スレッドを開始する境界を指定する。開始できない場合は例外を送出すること。 */
    static Handle create(CredentialManager credentials, OAuth2Credential token,
                         ScheduledThreadPoolExecutor scheduler, Options options, Consumer<Thread> requestStarter) {
        Objects.requireNonNull(credentials); Objects.requireNonNull(scheduler);
        var lifetime = new Lifetime(Objects.requireNonNull(requestStarter));
        var network = new okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false)
                .callTimeout(options.timeoutMillis(), TimeUnit.MILLISECONDS)
                .eventListener(lifetime).addInterceptor(chain -> {
                    if (lifetime.isClosed()) throw new IOException("HTTP client has ended");
                    return chain.proceed(chain.request());
                }).build();
        try {
            var tokens = new TwitchHelixTokenManager(credentials, options.clientId(), options.clientSecret(), token);
            var limits = new TwitchHelixRateLimitTracker(
                    io.github.bucket4j.BandwidthBuilder.builder().capacity(800)
                            .refillGreedy(800, Duration.ofMinutes(1)).build(), tokens);
            var sdk = new TwitchHelixHttpClient(new feign.okhttp.OkHttpClient(network), scheduler, tokens, limits, options.timeoutMillis());
            feign.Client transport = (request, settings) -> {
                lifetime.enter();
                try {
                    var response = sdk.execute(request, settings);
                    if (lifetime.isClosed()) {
                        if (response != null) response.close();
                        throw cancelled(null);
                    }
                    return response;
                } catch (IOException failure) {
                    if (lifetime.isClosed()) throw cancelled(failure);
                    throw failure;
                }
                finally { lifetime.leave(); }
            };
            var headers = new TwitchHelixClientIdInterceptor(Twitch4JGlobal.userAgent, tokens);
            var mapper = TypeConvert.getObjectMapper();
            var encoder = mapper.copy().addMixIn(CustomReward.class, CustomRewardEncodeMixIn.class);
            var errors = new TwitchHelixErrorDecoder(new JacksonDecoder(), limits);
            var responses = new TwitchHelixDecoder(mapper, limits);
            var client = Feign.builder().client(transport)
                    .encoder(new JacksonEncoder(encoder)).decoder((response, type) -> {
                        lifetime.enter();
                        try {
                            var result = responses.decode(response, type);
                            if (lifetime.isClosed()) throw cancelled(null);
                            return result;
                        } catch (IOException | RuntimeException failure) {
                            if (lifetime.isClosed()) throw cancelled(failure);
                            throw failure;
                        } finally { lifetime.leave(); }
                    })
                    .errorDecoder((method, response) -> {
                        lifetime.enter();
                        try {
                            var failure = errors.decode(method, response);
                            if (lifetime.isClosed()) return cancelled(null);
                            if (response.status() == 429) return new RetryableException(429, "Twitch rate limit exceeded",
                                    response.request().httpMethod(), failure, (Long) null, response.request());
                            return failure;
                        } catch (RuntimeException failure) {
                            if (lifetime.isClosed()) return cancelled(failure);
                            throw failure;
                        } finally { lifetime.leave(); }
                    })
                    .requestInterceptor(template -> {
                        lifetime.enter();
                        try {
                            headers.apply(template);
                            if (lifetime.isClosed()) throw cancelled(null);
                        } finally { lifetime.leave(); }
                    })
                    .options(new Request.Options(options.timeoutMillis() / 3, TimeUnit.MILLISECONDS,
                            options.timeoutMillis(), TimeUnit.MILLISECONDS, true))
                    .retryer(Retryer.NEVER_RETRY)
                    .decodeVoid().target(HelixClient.class, options.baseUrl());
            return new Handle(owned(client, lifetime, options.timeoutMillis()), network, lifetime);
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, () -> release(network, lifetime));
            throw failure;
        }
    }

    private static HelixClient owned(HelixClient delegate, Lifetime lifetime, int timeoutMillis) {
        return (HelixClient) Proxy.newProxyInstance(HelixClient.class.getClassLoader(), new Class<?>[] {HelixClient.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                        case "equals" -> proxy == arguments[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> delegate.toString();
                        default -> throw new IllegalStateException("unknown Object method");
                    };
                    var request = lifetime.startRequest(() -> method.invoke(delegate, arguments));
                    try { return request.get(timeoutMillis, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException failure) {
                        request.cancel(true);
                        Thread.currentThread().interrupt();
                        throw cancelled(failure);
                    } catch (TimeoutException failure) {
                        request.cancel(true);
                        throw new CompletionException(failure);
                    } catch (ExecutionException failure) {
                        var cause = failure.getCause();
                        throw cause instanceof InvocationTargetException invocation ? invocation.getCause() : cause;
                    }
                });
    }

    private static CancellationException cancelled(Throwable cause) {
        if (cause instanceof CancellationException cancelled) return cancelled;
        var cancelled = new CancellationException("HTTP client has ended");
        if (cause != null) cancelled.initCause(cause);
        return cancelled;
    }

    /** 受付済みHTTPと本文処理を所有する。外部closeは実終了を待ち、処理自身のcloseは停止だけを要求する。 */
    private static final class Lifetime extends okhttp3.EventListener {
        private final Consumer<Thread> requestStarter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Object ownership = new Object();
        // 要求全体とcodec等の入れ子を分け、内側のleaveで実行中の所有を失わない。
        private final HashMap<Thread, Integer> executing = new HashMap<>();
        private final Set<FutureTask<?>> requests = new HashSet<>();
        private final Set<okhttp3.Call> calls = new HashSet<>();
        private boolean shutdownFinished;

        Lifetime(Consumer<Thread> requestStarter) { this.requestStarter = requestStarter; }

        boolean isClosed() { return closed.get(); }
        boolean isProcessingOnCurrentThread() {
            synchronized (ownership) { return executing.containsKey(Thread.currentThread()); }
        }

        <T> FutureTask<T> startRequest(Callable<T> work) {
            var request = new FutureTask<>(work);
            var thread = Thread.ofVirtual().name("TCV-Helix-Request").unstarted(() -> {
                try { request.run(); }
                finally {
                    // Futureの取消は先に完了しても、実処理の所有はrunの終了まで維持する。
                    synchronized (ownership) { requests.remove(request); }
                    leave();
                }
            });
            synchronized (ownership) {
                if (closed.get()) throw cancelled(null);
                requests.add(request);
                executing.put(thread, 1);
            }
            try { requestStarter.accept(thread); }
            catch (RuntimeException | Error failure) {
                synchronized (ownership) {
                    requests.remove(request);
                    executing.remove(thread);
                    ownership.notifyAll();
                }
                request.cancel(false);
                throw failure;
            }
            return request;
        }

        void enter() {
            synchronized (ownership) {
                if (closed.get()) throw cancelled(null);
                executing.merge(Thread.currentThread(), 1, Integer::sum);
            }
        }

        void leave() {
            synchronized (ownership) {
                executing.computeIfPresent(Thread.currentThread(), (thread, depth) -> depth == 1 ? null : depth - 1);
                ownership.notifyAll();
            }
        }

        @Override public void callStart(okhttp3.Call call) {
            final boolean rejected;
            synchronized (ownership) {
                rejected = closed.get();
                if (!rejected) calls.add(call);
            }
            if (rejected) call.cancel();
        }
        @Override public void callEnd(okhttp3.Call call) { forget(call); }
        @Override public void callFailed(okhttp3.Call call, IOException failure) { forget(call); }
        private void forget(okhttp3.Call call) {
            synchronized (ownership) {
                calls.remove(call);
                ownership.notifyAll();
            }
        }

        void close(Runnable shutdownNetwork) {
            final List<okhttp3.Call> pending;
            final List<FutureTask<?>> pendingRequests;
            final boolean owner;
            synchronized (ownership) {
                owner = closed.compareAndSet(false, true);
                // Interrupt before a released worker can move to another owner's work.
                if (owner) executing.keySet().forEach(Thread::interrupt);
                pending = owner ? List.copyOf(calls) : List.of();
                pendingRequests = owner ? List.copyOf(requests) : List.of();
            }
            if (!owner) {
                awaitDrained();
                return;
            }
            try {
                CloseActions.runAll(() -> pendingRequests.forEach(request -> request.cancel(true)),
                        () -> pending.forEach(okhttp3.Call::cancel), shutdownNetwork);
            } finally {
                synchronized (ownership) {
                    shutdownFinished = true;
                    ownership.notifyAll();
                }
                awaitDrained();
            }
        }

        private void awaitDrained() {
            boolean interrupted = false;
            try {
                synchronized (ownership) {
                    if (executing.containsKey(Thread.currentThread())) return;
                    while (!shutdownFinished || !executing.isEmpty() || !calls.isEmpty()) {
                        try { ownership.wait(); }
                        catch (InterruptedException cancellation) { interrupted = true; }
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private static void release(okhttp3.OkHttpClient network, Lifetime lifetime) {
        lifetime.close(() -> CloseActions.runAll(network.dispatcher()::cancelAll,
                () -> network.dispatcher().executorService().shutdownNow(), network.connectionPool()::evictAll));
    }
}
