package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.support.TestJvm;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.helix.TwitchHelixBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.sun.net.httpserver.HttpServer;
import com.netflix.config.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowedChannelsHttpTest {
    @Test
    void aDecoderCanReenterSdkCloseWhileAnotherThreadIsWaitingForIt(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var command = new ArrayList<String>();
        command.add(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString());
        java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("mockito-core"))
                .forEach(command::add);
        command.addAll(List.of("--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                SdkCloseReentryProbe.class.getName()));
        var log = directory.resolve("sdk-close-reentry.log");
        var process = TestJvm.utf8(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try (var input = process.getInputStream(); var output = process.getOutputStream(); var errors = process.getErrorStream()) {
            try {
                assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).withFailMessage("child exit " + process.exitValue() + "\n"
                        + java.nio.file.Files.readString(log)).isZero();
                assertThat(java.nio.file.Files.readString(log)).contains("sdkCloseReentryCompleted=true");
            } finally {
                if (process.isAlive()) process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    public static final class SdkCloseReentryProbe {
        public static void main(String[] arguments) throws Exception {
            var sdk = new java.util.concurrent.atomic.AtomicReference<TwitchClientFactory.Resources>();
            var decoding = new CountDownLatch(1);
            var reentryReturned = new CountDownLatch(1);
            try (var decoders = mockConstruction(com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.class,
                    (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                        var decoded = new feign.jackson.JacksonDecoder(com.github.twitch4j.common.util.TypeConvert.getObjectMapper())
                                .decode(call.getArgument(0), call.getArgument(1));
                        decoding.countDown();
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException shutdown) {
                            sdk.get().close();
                            reentryReturned.countDown();
                        }
                        return decoded;
                    }));
                 var server = new Endpoint(request -> page(List.of(), null));
                 var closer = Executors.newVirtualThreadPerTaskExecutor()) {
                sdk.set(server.sdk);
                var request = new CompletableFuture<Throwable>();
                var caller = Thread.ofVirtual().start(() -> {
                    try { server.api.getFollowChannelOwners(); request.complete(null); }
                    catch (Throwable failure) { request.complete(failure); }
                });
                assertThat(decoding.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = closer.submit(server.sdk::close);
                try { closing.get(5, TimeUnit.SECONDS); }
                catch (TimeoutException selfWait) {
                    System.out.println("sdkCloseReentrySelfWaitDetected=true");
                    // 子JVMの待機だけを終了し、親のテスト実行へデッドロックを持ち越さない。
                    Runtime.getRuntime().halt(31);
                }
                assertThat(reentryReturned.getCount()).isZero();
                assertThat(request.get(5, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
                caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
                System.out.println("sdkCloseReentryCompleted=true");
            }
        }
    }

    @Test
    void externalCloseStillWaitsAfterTheDecoderRequestedTheSdkShutdown() throws Exception {
        var sdk = new java.util.concurrent.atomic.AtomicReference<TwitchClientFactory.Resources>();
        var selfCloseReturned = new CountDownLatch(1);
        var releaseCleanup = new CountDownLatch(1);
        var decoderExited = new CountDownLatch(1);
        try (var decoders = mockConstruction(com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    var decoded = new feign.jackson.JacksonDecoder(com.github.twitch4j.common.util.TypeConvert.getObjectMapper())
                            .decode(call.getArgument(0), call.getArgument(1));
                    sdk.get().close();
                    selfCloseReturned.countDown();
                    try {
                        boolean interrupted = false;
                        try {
                            while (true) {
                                try {
                                    if (!releaseCleanup.await(5, TimeUnit.SECONDS)) throw new AssertionError("decoder cleanup not released");
                                    break;
                                } catch (InterruptedException cancellation) { interrupted = true; }
                            }
                        } finally { if (interrupted) Thread.currentThread().interrupt(); }
                        return decoded;
                    } finally { decoderExited.countDown(); }
                }));
             var server = new Endpoint(request -> page(List.of(), null));
             var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            sdk.set(server.sdk);
            var callerDone = new CompletableFuture<Throwable>();
            var caller = Thread.ofVirtual().start(() -> {
                try { server.api.getFollowChannelOwners(); callerDone.complete(null); }
                catch (Throwable failure) { callerDone.complete(failure); }
            });
            try {
                assertThat(selfCloseReturned.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = closer.submit(server.sdk::close);

                assertThatThrownBy(() -> closing.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(decoderExited.getCount()).isEqualTo(1);
                releaseCleanup.countDown();
                closing.get(5, TimeUnit.SECONDS);
                assertThat(decoderExited.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(callerDone.get(5, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
                assertThat(server.requests).hasSize(1);
            } finally {
                releaseCleanup.countDown();
                caller.interrupt();
                caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
            }
        }
    }

    @Test
    void externalClientCloseWaitsForDecoderCleanupAfterTheCallerWasCancelled() throws Exception {
        verifyExternalClose(false);
    }

    @Test
    void anInterruptedExternalCloseStillWaitsAndPreservesItsInterruption() throws Exception {
        verifyExternalClose(true);
    }

    private void verifyExternalClose(boolean interruptClosing) throws Exception {
        var decoding = new CountDownLatch(1);
        var cleanupStarted = new CountDownLatch(1);
        var releaseCleanup = new CountDownLatch(1);
        var decoderExited = new CountDownLatch(1);
        try (var decoders = mockConstruction(com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    var decoded = new feign.jackson.JacksonDecoder(com.github.twitch4j.common.util.TypeConvert.getObjectMapper())
                            .decode(call.getArgument(0), call.getArgument(1));
                    decoding.countDown();
                    try {
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException cancelled) {
                            cleanupStarted.countDown();
                            while (true) {
                                try {
                                    if (!releaseCleanup.await(5, TimeUnit.SECONDS)) throw new AssertionError("decoder cleanup not released");
                                    break;
                                } catch (InterruptedException repeatedCancellation) { }
                            }
                            Thread.currentThread().interrupt();
                        }
                        return decoded;
                    } finally { decoderExited.countDown(); }
                }));
             var server = new Endpoint(request -> page(List.of(), null));
             var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            var callerDone = new CompletableFuture<Throwable>();
            var caller = Thread.ofVirtual().start(() -> {
                try { server.api.getFollowChannelOwners(); callerDone.complete(null); }
                catch (Throwable failure) { callerDone.complete(failure); }
            });
            try {
                assertThat(decoding.await(5, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(callerDone.get(5, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
                var closingStarted = new CountDownLatch(1);
                var closingThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
                var closing = closer.submit(() -> {
                    closingThread.set(Thread.currentThread());
                    closingStarted.countDown();
                    server.sdk.close();
                    return Thread.currentThread().isInterrupted();
                });
                assertThat(closingStarted.await(5, TimeUnit.SECONDS)).isTrue();
                if (interruptClosing) closingThread.get().interrupt();

                assertThatThrownBy(() -> closing.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(decoderExited.getCount()).isEqualTo(1);
                releaseCleanup.countDown();
                assertThat(closing.get(5, TimeUnit.SECONDS)).isEqualTo(interruptClosing);
                assertThat(decoderExited.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(server.requests).hasSize(1);
                verify(server.twitch, never()).invalidateCredentials();
            } finally {
                releaseCleanup.countDown();
                caller.interrupt();
                caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
            }
        }
    }

    @Test
    void streamMonitoringReadsTheSessionTokenEvenWhenSdkDefaultsDiffer() throws Exception {
        var sdkDefault = new OAuth2Credential("twitch", "test-token", null, "own-user", "Test", 3600, new ArrayList<>());
        try (var server = new Endpoint(request -> "{\"data\":[]}", request -> 200, sdkDefault)) {
            when(server.twitch.getAccessToken()).thenReturn("rotated-token");

            pollStreamsOnce(server);

            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().authorization()).isEqualTo("Bearer rotated-token");
        }
    }

    @Test
    void streamMonitoringKeepsWorkingAfterTheSdkEnrichesTheOriginalCredential() throws Exception {
        var credential = new OAuth2Credential("twitch", "test-token", null, "own-user", "Test", 3600, new ArrayList<>());
        try (var server = new Endpoint(request -> "{\"data\":[]}", request -> 200, credential)) {
            when(server.twitch.getAccessToken()).thenAnswer(ignored -> credential.getAccessToken());
            credential.updateCredential(new OAuth2Credential("twitch", "rotated-token", null, "own-user", "Test", 3600, List.of()));

            pollStreamsOnce(server);

            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().path()).isEqualTo("/helix/streams");
            assertThat(server.requests.getFirst().query()).containsEntry("user_id", List.of("42"));
            assertThat(server.requests.getFirst().authorization()).isEqualTo("Bearer rotated-token");
        }
    }

    @Test
    void aMonitoring401RetriesWithTheRotatedSessionToken() throws Exception {
        var credential = new OAuth2Credential("twitch", "test-token", null, "own-user", "Test", 3600, new ArrayList<>());
        try (var server = new Endpoint(request -> "{\"data\":[]}",
                request -> request.authorization().equals("Bearer test-token") ? 401 : 200, credential);
             var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                     when(controller.refreshToken()).thenAnswer(ignored -> {
                         credential.updateCredential(new OAuth2Credential("twitch", "rotated-token", null, "own-user", "Test", 3600, List.of()));
                         return credential;
                     }))) {
            when(server.twitch.getAccessToken()).thenAnswer(ignored -> credential.getAccessToken());

            pollStreamsOnce(server);

            assertThat(server.requests.stream().map(Request::authorization)).containsExactly("Bearer test-token", "Bearer rotated-token");
            verify(server.twitch, never()).invalidateCredentials();
            assertThat(controllers.constructed()).hasSize(1); verify(controllers.constructed().getFirst()).close();
        }
    }

    private static void pollStreamsOnce(Endpoint server) throws Exception {
        var scheduler = spy(new ScheduledThreadPoolExecutor(1));
        var polls = new ArrayDeque<Runnable>();
        doAnswer(call -> {
            polls.addLast(call.getArgument(0)); return mock(ScheduledFuture.class);
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        try (var api = new TwitchAPI(server.twitch, scheduler)) {
            var channel = mock(Broadcaster.class);
            when(channel.getUserId()).thenReturn("42"); when(channel.getUserLogin()).thenReturn("watched");
            assertThat(api.enableStreamEventListener(channel)).isTrue();
            assertThat(polls).isNotEmpty(); polls.removeFirst().run();
        } finally {
            scheduler.shutdownNow(); polls.clear();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void malformedJsonFailsWithoutCancellationOrAutomaticRetryAndTheClientRemainsUsable() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> attempts.incrementAndGet() == 1 ? "{\"data\":" : page(List.of(), null))) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(CancellationException.class);
            assertThat(server.requests).hasSize(1);
            assertThat(server.http.isClosed()).isFalse();
            assertThat(server.api.getFollowChannelOwners()).isEmpty();
            assertThat(server.requests).hasSize(2);
            verify(server.twitch, never()).invalidateCredentials();
        }
    }

    @ParameterizedTest(name = "late decoder result for HTTP {0}")
    @ValueSource(ints = {200, 401, 429, 503})
    void aDecoderResultProducedAfterReentrantCloseIsDiscarded(int status) throws Exception {
        var closing = new java.util.concurrent.atomic.AtomicReference<HelixClientFactory.Handle>();
        try (var decoders = mockConstruction(com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    var result = new feign.jackson.JacksonDecoder(com.github.twitch4j.common.util.TypeConvert.getObjectMapper())
                            .decode(call.getArgument(0), call.getArgument(1));
                    closing.get().close();
                    return result;
                }));
             var errors = mockConstruction(com.github.twitch4j.helix.TwitchHelixErrorDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    final Exception failure;
                    if (status == 401) failure = mock(com.github.twitch4j.common.exception.UnauthorizedException.class);
                    else {
                        var httpFailure = mock(feign.FeignException.class);
                        when(httpFailure.status()).thenReturn(status); failure = httpFailure;
                    }
                    closing.get().close();
                    return failure;
                }));
             var server = new Endpoint(request -> page(List.of(), null), request -> status)) {
            closing.set(server.http); clearInvocations(server.twitch);
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(CancellationException.class);
            verify(server.twitch, times(1)).getAccessToken();
            verify(server.twitch, never()).invalidateCredentials();
            assertThat(server.requests).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 401, 429, 503})
    void closingDuringResponseBodyReadingCancelsWithoutAnotherAttempt(int status) throws Exception {
        var reading = new CountDownLatch(1); var release = new CountDownLatch(1);
        var bodyWaiting = new CountDownLatch(1); var bodyStopped = new CountDownLatch(1);
        try (var decoders = mockConstruction(com.github.twitch4j.helix.interceptor.TwitchHelixDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    feign.Response response = call.getArgument(0);
                    var input = response.body().asInputStream();
                    assertThat(input.read()).isEqualTo((int) '{');
                    reading.countDown(); input.readAllBytes();
                    throw new AssertionError("incomplete response body returned successfully");
                }));
             var errors = mockConstruction(com.github.twitch4j.helix.TwitchHelixErrorDecoder.class,
                (decoder, context) -> when(decoder.decode(any(), any())).thenAnswer(call -> {
                    feign.Response response = call.getArgument(1);
                    try {
                        var input = response.body().asInputStream();
                        assertThat(input.read()).isEqualTo((int) '{');
                        reading.countDown(); input.readAllBytes();
                    }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    throw new AssertionError("incomplete error body returned successfully");
                }));
             var server = new Endpoint(request -> page(List.of(), null), request -> status, true, () -> {
                 try {
                     bodyWaiting.countDown();
                     if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("body was not released");
                 } catch (InterruptedException failure) {
                     Thread.currentThread().interrupt(); throw new AssertionError(failure);
                 } finally { bodyStopped.countDown(); }
                 return false;
             })) {
            clearInvocations(server.twitch);
            var done = new CompletableFuture<Throwable>();
            var caller = Thread.ofVirtual().start(() -> {
                try { server.api.getFollowChannelOwners(); done.complete(null); }
                catch (Throwable failure) { done.complete(failure); }
            });
            try {
                assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(bodyWaiting.await(5, TimeUnit.SECONDS)).isTrue();
                server.http.close();
                try { assertThat(done.get(2, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class); }
                catch (TimeoutException failure) {
                    Thread.getAllStackTraces().forEach((thread, stack) -> {
                        if (Arrays.stream(stack).anyMatch(frame -> frame.getClassName().startsWith("feign.")))
                            System.err.println(thread + "\n" + String.join("\n", Arrays.stream(stack).map(Object::toString).toList()));
                    });
                    throw failure;
                }
                verify(server.twitch, times(1)).getAccessToken();
                verify(server.twitch, never()).invalidateCredentials();
                assertThat(server.requests).hasSize(1);
            } finally {
                release.countDown(); caller.interrupt(); caller.join(5000);
                assertThat(bodyStopped.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(caller.isAlive()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"日本語", "angel of death", "a+b/c==", "100%", "テスト & #/?",
            "literal%20value", "literal%2Bvalue", "literal%26value"})
    void searchKeywordsReachTheServerUnchanged(String keyword) throws Exception {
        try (var server = new Endpoint(request -> "{\"data\":[]}")) {
            assertThat(server.api.search(keyword, true)).isEmpty();
            assertThat(server.requests).hasSize(1);
            var request = server.requests.getFirst();
            assertThat(request.path()).isEqualTo("/helix/search/channels");
            assertThat(request.query()).containsEntry("query", List.of(keyword))
                    .containsEntry("first", List.of("40")).containsEntry("live_only", List.of("true"));
            assertThat(request.authorization()).isEqualTo("Bearer test-token");
        }
    }

    @Test
    void closingAnInFlightHttpRequestCancelsWithoutStartingAnotherAttempt() throws Exception {
        var received = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var server = new Endpoint(request -> page(List.of(), null), request -> {
            received.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("response was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt(); throw new AssertionError(failure);
            }
            return -1;
        })) {
            clearInvocations(server.twitch);
            var done = new CompletableFuture<Throwable>();
            var caller = Thread.ofVirtual().start(() -> {
                try { server.api.getFollowChannelOwners(); done.complete(null); }
                catch (Throwable failure) { done.complete(failure); }
            });
            try {
                assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
                server.http.close();
                assertThat(done.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
                verify(server.twitch, times(1)).getAccessToken();
                assertThat(server.requests).hasSize(1);
            } finally {
                release.countDown(); caller.interrupt(); caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
            }
        }
    }

    @Test
    void aDroppedConnectionRetriesInTheApplicationAndRecovers() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> request.path().endsWith("/users")
                ? users(request.query().getOrDefault("id",List.of())) : page(List.of("1"),null),
                request -> request.path().endsWith("/channels/followed") && attempts.incrementAndGet() == 1 ? -1 : 200)) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("1");
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void aPermanentDroppedConnectionStopsAtFiveHttpAttempts() throws Exception {
        try (var server = new Endpoint(request -> page(List.of(),null), request -> -1)) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(RuntimeException.class);
            assertThat(server.requests).hasSize(5);
        }
    }

    @Test
    void aTemporary429RecoversThroughTheSameApplicationRetryPolicy() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> request.path().endsWith("/users")
                ? users(request.query().getOrDefault("id",List.of())) : page(List.of("1"),null),
                request -> request.path().endsWith("/channels/followed") && attempts.incrementAndGet() == 1 ? 429 : 200)) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("1");
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void repeated429ResponsesDoNotCreateAnUnboundedSdkRetryLoop() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> page(List.of(),null), request -> attempts.incrementAndGet() <= 6 ? 429 : 200)) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(RuntimeException.class);
            assertThat(attempts).hasValue(5);
        }
    }

    @Test
    void closingTheHttpHandleInterruptsAnAcceptedTokenValidation() throws Exception {
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var server = new Endpoint(request -> page(List.of(), null))) {
            when(server.twitch.getAccessToken()).thenReturn("held-token");
            doAnswer(call -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("validation was not released");
                } catch (InterruptedException failure) {
                    interrupted.countDown(); Thread.currentThread().interrupt(); throw new CancellationException("validation ended");
                }
                OAuth2Credential incoming = call.getArgument(0); incoming.getContext().put("client_id", "test-client");
                return Optional.of(incoming);
            }).when(server.identity).getAdditionalCredentialInformation(any());
            var done = new CompletableFuture<Throwable>();
            var caller = Thread.ofVirtual().start(() -> {
                try { server.api.getFollowChannelOwners(); done.complete(null); }
                catch (Throwable failure) { done.complete(failure); }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                server.http.close();
                assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(done.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
                assertThat(server.requests).isEmpty();
            } finally {
                release.countDown(); caller.interrupt(); caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
            }
        }
    }

    @Test
    void closingTheHttpHandleRejectsNewRequestsAndLeavesTheBorrowedSchedulerUsable() throws Exception {
        try (var server = new Endpoint(request -> page(List.of(), null))) {
            server.http.close(); server.http.close();
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(CancellationException.class);
            assertThat(server.requests).isEmpty();
            assertThat(server.sdkScheduler.submit(() -> "still open").get(5, TimeUnit.SECONDS)).isEqualTo("still open");
        }
    }

    @Test
    void aClosedHttpHandleDoesNotValidateNewTokens() throws Exception {
        try (var server = new Endpoint(request -> page(List.of(), null))) {
            server.http.close(); clearInvocations(server.identity);
            when(server.twitch.getAccessToken()).thenReturn("new-token-after-close");
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(CancellationException.class);
            verify(server.identity, never()).getAdditionalCredentialInformation(any());
            assertThat(server.requests).isEmpty();
        }
    }

    @Test
    void theApplicationOwnsAllFiveHttpAttempts() throws Exception {
        try (var server = new Endpoint(request -> page(List.of("1"), "next"),
                request -> request.query().containsKey("after") ? 503 : 200)) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(RuntimeException.class);
            assertThat(server.requests.stream().filter(r -> r.query().containsKey("after"))).hasSize(5);
            assertThat(server.requests).noneMatch(r -> r.path().endsWith("/users"));
        }
    }

    @Test
    void buildingTheApplicationClientDoesNotOverwriteGlobalHystrixSettings() throws Exception {
        try (var server = new Endpoint(request -> page(List.of(), null))) {
            var config = ConfigurationManager.getConfigInstance();
            server.previousHystrix.forEach((key,value) -> assertThat(config.getProperty(key)).isEqualTo(value));
        }
    }

    @Test
    void closingTheSessionBetweenPagesCancelsWithoutReturningTheFirstPage() throws Exception {
        var closing = new java.util.concurrent.atomic.AtomicBoolean();
        try (var server = new Endpoint(request -> { closing.set(true); return page(List.of("1"), "next"); })) {
            when(server.twitch.isClosing()).thenAnswer(ignored -> closing.get());
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(CancellationException.class);
            assertThat(server.requests).hasSize(1);
            verify(server.twitch, never()).invalidateCredentials();
        }
    }

    @Test
    void aTemporary503RetriesTheSamePageWithoutRestartingOrDuplicatingResults() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> {
            if (!request.path().endsWith("/channels/followed")) return users(request.query().getOrDefault("id", List.of()));
            return request.query().containsKey("after") ? page(List.of("2"), null) : page(List.of("1"), "next");
        }, request -> request.query().containsKey("after") && attempts.incrementAndGet() == 1 ? 503 : 200)) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("1", "2");
            assertThat(attempts).hasValue(2);
            assertThat(server.requests.stream().filter(r -> r.query().containsKey("after")).map(r -> r.query().get("after")))
                    .containsExactly(List.of("next"), List.of("next"));
            verify(server.twitch, never()).invalidateCredentials();
        }
    }

    @Test
    void aPermanent503HasBoundedSdkRetriesAndDoesNotReturnPartialData() throws Exception {
        try (var server = new Endpoint(request -> page(List.of("1"), "next"),
                request -> request.query().containsKey("after") ? 503 : 200, false)) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(RuntimeException.class);
            // Twitch4J 1.21.0 retries twice inside each of the application's five SDK attempts.
            assertThat(server.requests.stream().filter(r -> r.query().containsKey("after"))).hasSize(10);
            assertThat(server.requests).noneMatch(r -> r.path().endsWith("/users"));
            verify(server.twitch, never()).invalidateCredentials();
        }
    }

    @Test
    void aReal401RefreshesOnceAndUsesTheRotatedTokenForTheRetriedPageAndDetails() throws Exception {
        try (var server = new Endpoint(request -> request.path().endsWith("/users")
                ? users(request.query().getOrDefault("id", List.of())) : page(List.of("1"), null),
                request -> request.authorization().equals("Bearer test-token") ? 401 : 200);
             var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                     when(controller.refreshToken()).thenAnswer(ignored -> {
                         when(server.twitch.getAccessToken()).thenReturn("rotated-token");
                         return new OAuth2Credential("twitch", "rotated-token", null, "own-user", "Test User", 3600, List.of("user:read:follows"));
                     }))) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("1");
            assertThat(server.requests.stream().map(Request::authorization)).containsExactly(
                    "Bearer test-token", "Bearer rotated-token", "Bearer rotated-token");
            assertThat(controllers.constructed()).hasSize(1); verify(controllers.constructed().getFirst()).close();
            verify(server.twitch, never()).invalidateCredentials();
        }
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = "   ")
    void anEmptyFinalPageStopsWithoutLookingUpUsers(String cursor) throws Exception {
        try (var server = new Endpoint(request -> page(List.of(), cursor))) {
            assertThat(server.api.getFollowChannelOwners()).isEmpty();
            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().path()).endsWith("/channels/followed");
        }
    }

    @Test
    void anEmptyIntermediatePageAdvancesUsingTheUnmodifiedOpaqueCursor() throws Exception {
        var cursor = "a+b/c==";
        try (var server = new Endpoint(request -> {
            if (!request.path().endsWith("/channels/followed")) return users(request.query().getOrDefault("id", List.of()));
            return request.query().containsKey("after") ? page(List.of("3"), null) : page(List.of(), cursor);
        })) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("3");
            var follows = server.requests.stream().filter(r -> r.path().endsWith("/channels/followed")).toList();
            assertThat(follows).hasSize(2);
            assertThat(follows.getLast().query()).containsEntry("after", List.of(cursor));
        }
    }

    @Test
    void userDetailsAreRequestedInBatchesOfAtMostOneHundredAfterAllPagesSucceed() throws Exception {
        var ids = java.util.stream.IntStream.rangeClosed(1,201).mapToObj(Integer::toString).toList();
        try (var server = new Endpoint(request -> {
            if (!request.path().endsWith("/channels/followed")) return users(request.query().getOrDefault("id", List.of()));
            var after = request.query().getOrDefault("after", List.of("start")).getFirst();
            return switch (after) {
                case "start" -> page(ids.subList(0,100), "second");
                case "second" -> page(ids.subList(100,200), "third");
                default -> page(ids.subList(200,201), null);
            };
        })) {
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactlyElementsOf(ids);
            assertThat(server.requests.stream().map(Request::path)).containsExactly(
                    "/helix/channels/followed", "/helix/channels/followed", "/helix/channels/followed",
                    "/helix/users", "/helix/users", "/helix/users");
            var batches = server.requests.stream().filter(r -> r.path().endsWith("/users")).map(r -> r.query().get("id")).toList();
            assertThat(batches).extracting(List::size).containsExactly(100,100,1);
            assertThat(batches.stream().flatMap(List::stream)).containsExactlyElementsOf(ids);
        }
    }

    @Test
    void aRepeatedCursorFailsInsteadOfReturningAPartialFollowingList() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var server = new Endpoint(request -> {
            if (!request.path().endsWith("/channels/followed")) return users(request.query().getOrDefault("id", List.of()));
            return switch (calls.incrementAndGet()) {
                case 1 -> page(List.of("1"), "same");
                case 2 -> page(List.of("2"), "same");
                default -> page(List.of("4"), null);
            };
        })) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Repeated followed channels pagination cursor");
            assertThat(calls).hasValue(2);
            assertThat(server.requests).noneMatch(request -> request.path().endsWith("/users"));
            assertThat(server.api.getFollowChannelOwners()).extracting(Broadcaster::getUserId).containsExactly("4");
            assertThat(calls).hasValue(3);
            assertThat(server.requests.get(2).query()).doesNotContainKey("after");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-data", "null-data", "missing-pagination", "null-pagination", "null-page", "null-row", "missing-id", "blank-id"})
    void malformedPagesNeverProduceASuccessfulPartialResult(String kind) throws Exception {
        var invalid = switch (kind) {
            case "missing-data" -> "{\"pagination\":{}}";
            case "null-data" -> "{\"data\":null,\"pagination\":{}}";
            case "missing-pagination" -> "{\"data\":[]}";
            case "null-pagination" -> "{\"data\":[],\"pagination\":null}";
            case "null-page" -> "null";
            case "null-row" -> "{\"data\":[null],\"pagination\":{}}";
            case "missing-id" -> "{\"data\":[{}],\"pagination\":{}}";
            default -> "{\"data\":[{\"broadcaster_id\":\"  \"}],\"pagination\":{}}";
        };
        try (var server = new Endpoint(request -> request.path().endsWith("/channels/followed")
                ? request.query().containsKey("after") ? invalid : page(List.of("1"), "next")
                : users(request.query().getOrDefault("id", List.of())))) {
            assertThatThrownBy(server.api::getFollowChannelOwners).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("followed channels response");
            assertThat(server.requests).noneMatch(request -> request.path().endsWith("/users"));
        }
    }

    @Test
    void overlappingPagesReturnEachBroadcasterOnce() throws Exception {
        try (var server = new Endpoint(request -> {
            if (request.path().endsWith("/channels/followed")) {
                return request.query().containsKey("after") ? page(List.of("2", "3"), null) : page(List.of("1", "2"), "next");
            }
            return users(request.query().getOrDefault("id", List.of()));
        })) {
            var result = server.api.getFollowChannelOwners();

            assertThat(result).extracting(Broadcaster::getUserId).containsExactlyInAnyOrder("1", "2", "3");
            var follows = server.requests.stream().filter(request -> request.path().endsWith("/channels/followed")).toList();
            assertThat(follows).hasSize(2);
            assertThat(follows.getFirst().query()).containsEntry("user_id", List.of("own-user"))
                    .containsEntry("first", List.of("100")).doesNotContainKey("after");
            assertThat(follows.getLast().query()).containsEntry("after", List.of("next"));
            assertThat(server.requests).allSatisfy(request -> {
                assertThat(request.authorization()).isEqualTo("Bearer test-token");
                assertThat(request.clientId()).isEqualTo("test-client");
            });
            var details = server.requests.stream().filter(r -> r.path().endsWith("/users")).toList();
            assertThat(details).hasSize(1);
            assertThat(details.getFirst().query().get("id")).containsExactly("1", "2", "3");
        }
    }

    static String page(List<String> ids, String cursor) {
        var rows = ids.stream().map(id -> "{\"broadcaster_id\":\"" + id + "\",\"broadcaster_login\":\"user" + id + "\",\"broadcaster_name\":\"User " + id + "\",\"followed_at\":\"2026-01-01T00:00:00Z\"}").toList();
        return "{\"data\":[" + String.join(",", rows) + "],\"total\":3,\"pagination\":{" + (cursor == null ? "" : "\"cursor\":\"" + cursor + "\"") + "}}";
    }

    static String users(List<String> ids) {
        var rows = ids.stream().map(id -> "{\"id\":\"" + id + "\",\"login\":\"user" + id + "\",\"display_name\":\"User " + id + "\",\"profile_image_url\":\"\",\"offline_image_url\":\"\"}").toList();
        return "{\"data\":[" + String.join(",", rows) + "]}";
    }

    record Request(String path, Map<String, List<String>> query, String authorization, String clientId) {}

    static final class Endpoint implements AutoCloseable {
        private static final List<String> HYSTRIX_KEYS = List.of(
                "hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds",
                "hystrix.command.default.requestCache.enabled",
                "hystrix.threadpool.default.maxQueueSize",
                "hystrix.threadpool.default.queueSizeRejectionThreshold",
                "hystrix.command.TwitchHelix#banUser(String,String,String,BanUserInput).circuitBreaker.enabled",
                "hystrix.command.TwitchHelix#unbanUser(String,String,String,String).circuitBreaker.enabled");
        final Map<String, Object> previousHystrix = new LinkedHashMap<>();
        final List<Request> requests = new CopyOnWriteArrayList<>();
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final ExecutorService handler = Executors.newVirtualThreadPerTaskExecutor();
        final ScheduledThreadPoolExecutor sdkScheduler = new ScheduledThreadPoolExecutor(1);
        final HttpServer server;
        final TwitchAPI api;
        HelixClientFactory.Handle http;
        TwitchClientFactory.Resources sdk;
        final Twitch twitch = mock(Twitch.class);
        final TwitchIdentityProvider identity = mock(TwitchIdentityProvider.class);
        boolean closed;

        Endpoint(Function<Request, String> response) throws Exception { this(response, request -> 200); }

        Endpoint(Function<Request, String> response, ToIntFunction<Request> status) throws Exception { this(response, status, true); }

        Endpoint(Function<Request, String> response, ToIntFunction<Request> status, OAuth2Credential defaultToken) throws Exception {
            this(response, status, true, null, defaultToken);
        }

        Endpoint(Function<Request, String> response, ToIntFunction<Request> status, boolean application) throws Exception {
            this(response, status, application, null);
        }

        Endpoint(Function<Request, String> response, ToIntFunction<Request> status, boolean application,
                 BooleanSupplier sendRemainingBody) throws Exception {
            this(response, status, application, sendRemainingBody, null);
        }

        Endpoint(Function<Request, String> response, ToIntFunction<Request> status, boolean application,
                 BooleanSupplier sendRemainingBody, OAuth2Credential defaultToken) throws Exception {
            var config = ConfigurationManager.getConfigInstance();
            HYSTRIX_KEYS.forEach(key -> previousHystrix.put(key, config.getProperty(key)));
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(handler);
            server.createContext("/helix", exchange -> {
                try (exchange) {
                    var request = new Request(exchange.getRequestURI().getPath(), query(exchange.getRequestURI()),
                            exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Client-Id"));
                    requests.add(request);
                    var code = status.applyAsInt(request);
                    if (code < 0) return;
                    var body = code >= 400
                            ? "{\"error\":\"" + (code == 401 ? "Unauthorized" : code == 429 ? "Too Many Requests" : "Service Unavailable")
                                + "\",\"status\":" + code + ",\"message\":\"endpoint failure\"}"
                            : response.apply(request);
                    var bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    if (code == 429) {
                        exchange.getResponseHeaders().set("Ratelimit-Limit", "800");
                        exchange.getResponseHeaders().set("Ratelimit-Remaining", "0");
                        exchange.getResponseHeaders().set("Ratelimit-Reset", Long.toString(System.currentTimeMillis()/1000));
                    }
                    exchange.sendResponseHeaders(code, bytes.length);
                    if (sendRemainingBody == null) exchange.getResponseBody().write(bytes);
                    else {
                        exchange.getResponseBody().write(bytes, 0, 1); exchange.getResponseBody().flush();
                        if (sendRemainingBody.getAsBoolean()) exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                    }
                } catch (Throwable failure) { failures.add(failure); }
            });
            server.start();
            try {
                when(identity.getAdditionalCredentialInformation(any())).thenAnswer(call -> {
                    OAuth2Credential incoming = call.getArgument(0);
                    var credential = new OAuth2Credential("twitch", incoming.getAccessToken(), null, "own-user", "Test User", 3600, List.of("user:read:follows"));
                    credential.getContext().put("client_id", "test-client");
                    return Optional.of(credential);
                });
                var credentials = mock(CredentialManager.class);
                when(credentials.getIdentityProviderByName("twitch", TwitchIdentityProvider.class)).thenReturn(Optional.of(identity));
                var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/helix";
                if (application) http = HelixClientFactory.create(credentials, defaultToken, sdkScheduler,
                        new HelixClientFactory.Options("test-client", null, url, 3000));
                var helix = application
                        ? http.client()
                        : upstream(TwitchHelixBuilder.builder().withClientId("test-client").withCredentialManager(credentials)
                                .withBaseUrl(url).withTimeout(3000).withScheduledThreadPoolExecutor(sdkScheduler).build());
                final TwitchClients client;
                if (application) {
                    var factory = new TwitchClientFactory(() -> sdkScheduler,
                            () -> com.github.twitch4j.common.util.EventManagerUtils.initializeEventManager(
                                    com.github.philippheuer.events4j.simple.SimpleEventHandler.class),
                            context -> http, context -> mock(com.github.twitch4j.chat.TwitchChat.class));
                    sdk = factory.open(credentials, null); client = sdk.client();
                } else {
                    client = mock(TwitchClients.class); when(client.getHelix()).thenReturn(helix);
                }
                when(twitch.getClient()).thenReturn(client);
                when(twitch.getAccessToken()).thenReturn("test-token"); when(twitch.getUserId()).thenReturn("own-user");
                api = new TwitchAPI(twitch);
            } catch (RuntimeException | Error failure) {
                try {
                    CloseActions.runAll(() -> {
                        if (failure instanceof RuntimeException exception) throw exception;
                        throw (Error) failure;
                    }, this::closeClient, sdkScheduler::shutdownNow, () -> server.stop(0), handler::shutdownNow);
                }
                finally { restoreHystrix(); }
                throw failure;
            }
        }

        private static HelixClient upstream(com.github.twitch4j.helix.TwitchHelix sdk) {
            return (HelixClient) java.lang.reflect.Proxy.newProxyInstance(HelixClient.class.getClassLoader(),
                    new Class<?>[] {HelixClient.class}, (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) return method.invoke(sdk, arguments);
                        try {
                            var operation = (com.netflix.hystrix.HystrixCommand<?>) com.github.twitch4j.helix.TwitchHelix.class
                                    .getMethod(method.getName(), method.getParameterTypes()).invoke(sdk, arguments);
                            return operation.execute();
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        } catch (com.netflix.hystrix.exception.HystrixRuntimeException failure) {
                            if (failure.getFailureType() == com.netflix.hystrix.exception.HystrixRuntimeException.FailureType.TIMEOUT) {
                                throw new CompletionException(new TimeoutException("upstream SDK request timed out"));
                            }
                            throw failure.getCause();
                        }
                    });
        }

        static Map<String, List<String>> query(URI uri) {
            var result = new LinkedHashMap<String, List<String>>();
            if (uri.getRawQuery() != null) for (var pair : uri.getRawQuery().split("&")) {
                var parts = pair.split("=",2);
                result.computeIfAbsent(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), ignored -> new ArrayList<>())
                        .add(URLDecoder.decode(parts.length == 1 ? "" : parts[1], StandardCharsets.UTF_8));
            }
            return result;
        }

        @Override public void close() throws Exception {
            if (closed) return;
            closed = true;
            try {
                CloseActions.runAll(() -> {
                    try { api.close(); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }, this::closeClient, sdkScheduler::shutdownNow,
                        () -> server.stop(0), handler::shutdownNow);
            } finally {
                restoreHystrix();
                assertThat(sdkScheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                assertThat(handler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(failures).isEmpty();
        }

        private void closeClient() {
            if (sdk != null) sdk.close(); else if (http != null) http.close();
        }

        private void restoreHystrix() {
            var config = ConfigurationManager.getConfigInstance();
            previousHystrix.forEach((key, value) -> {
                if (value == null) config.clearProperty(key); else config.setProperty(key, value);
            });
        }
    }
}
