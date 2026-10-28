package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.support.TestJvm;

import com.github.philippheuer.credentialmanager.CredentialManager;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

class HelixRuntimeLinkageTest {
    @Test
    void applicationHttpClientCanBeCreatedWithoutTheLegacyCircuitBreakerRuntime(@TempDir Path directory) throws Exception {
        runProbe(directory, "construct", "legacyFreeClientCreated=true");
    }

    @Test
    void theProductionChatAndHttpClientsCanInitializeAndCloseWithOnlyShippedDependencies(@TempDir Path directory) throws Exception {
        runProbe(directory, "clients", "productionClientsConstructedAndClosed=true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"followed", "search", "users", "streams", "global-badges", "channel-badges", "clips"})
    void usedHelixEndpointsWorkWithoutTheLegacyCircuitBreakerRuntime(String operation, @TempDir Path directory) throws Exception {
        runProbe(directory, "http:" + operation, "legacyFreeHttpDecoded=" + operation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "error"})
    void failedWorkerStartupDoesNotLeaveARequestPreventingClientClose(String mode, @TempDir Path directory) throws Exception {
        runProbe(directory, "start:" + mode, "failedWorkerReleased=" + mode);
    }

    @Test
    void closeWaitsForAnAcceptedWorkerThatHasNotStartedYet(@TempDir Path directory) throws Exception {
        runProbe(directory, "pending", "pendingWorkerReleased=true");
    }

    @Test
    void aRequestDeadlineClosesAConnectionWhoseHeadersKeepArriving(@TempDir Path directory) throws Exception {
        runProbe(directory, "headers-timeout", "headersTimedOutPhysically=true");
    }

    private void runProbe(Path directory, String mode, String expected) throws Exception {
        var production = System.getProperty("tcv.test.production.classpath");
        assertThat(production).as("actual production runtime, without test-only SDK dependencies").isNotBlank();
        var fixtures = Path.of(HelixRuntimeLinkageTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var testLibraries = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> {
                    var name = Path.of(entry).getFileName().toString();
                    return name.startsWith("mockito-core-") || name.startsWith("byte-buddy-")
                            || name.startsWith("objenesis-") || name.startsWith("assertj-core-")
                            || name.startsWith("opentest4j-");
                });
        // SDK比較用の広いtestRuntimeClasspathは子JVMへ渡さず、検証fixtureだけを補う。
        var classpath = java.util.stream.Stream.concat(Arrays.stream(production.split(File.pathSeparator)),
                java.util.stream.Stream.concat(java.util.stream.Stream.of(fixtures), testLibraries))
                .distinct().collect(Collectors.joining(File.pathSeparator));
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("mockito-core"))
                .forEach(command::add);
        command.addAll(List.of("-cp", classpath, LegacyFreeClientProbe.class.getName(), mode));
        var log = directory.resolve("legacy-free-client.log");
        var process = TestJvm.utf8(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try (var input = process.getInputStream(); var output = process.getOutputStream(); var errors = process.getErrorStream()) {
            try {
                assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
                assertThat(Files.readString(log)).contains(expected);
            } finally {
                if (process.isAlive()) process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    public static final class LegacyFreeClientProbe {
        public static void main(String[] arguments) throws Exception {
            assertThat(ClassLoader.getSystemResource("com/netflix/hystrix/HystrixCommand.class")).isNull();
            assertThat(ClassLoader.getSystemResource("feign/hystrix/HystrixFeign.class")).isNull();
            assertThat(ClassLoader.getSystemResource("rx/Observable.class")).isNull();
            assertThat(ClassLoader.getSystemResource("com/netflix/config/ConfigurationManager.class")).isNull();
            assertThat(ClassLoader.getSystemResource("org/HdrHistogram/AbstractHistogram.class")).isNull();
            assertThat(ClassLoader.getSystemResource("org/apache/commons/configuration/AbstractConfiguration.class")).isNull();
            assertThat(ClassLoader.getSystemResource("org/apache/commons/lang/StringUtils.class")).isNull();
            assertThat(ClassLoader.getSystemResource("org/apache/commons/logging/LogFactory.class")).isNull();
            if (arguments[0].equals("clients")) {
                verifyProductionClients();
                return;
            }
            if (arguments[0].equals("headers-timeout")) {
                verifyHeadersTimeout();
                return;
            }
            if (arguments[0].equals("pending")) {
                verifyPendingWorker();
                return;
            }
            if (arguments[0].startsWith("start:")) {
                verifyFailedStartup(arguments[0].substring("start:".length()));
                return;
            }
            if (arguments[0].startsWith("http:")) {
                loadEndpoint(arguments[0].substring("http:".length()));
                return;
            }
            var scheduler = new ScheduledThreadPoolExecutor(1);
            try (var client = HelixClientFactory.create(mock(CredentialManager.class), null, scheduler,
                    new HelixClientFactory.Options("test-client", null, "http://127.0.0.1:1/helix", 1000))) {
                assertThat(client.client()).isNotNull();
                System.out.println("legacyFreeClientCreated=true");
            } finally {
                scheduler.shutdownNow();
                assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }

        private static void verifyProductionClients() throws Exception {
            var stored = new com.github.philippheuer.credentialmanager.domain.OAuth2Credential(
                    "twitch", "synthetic-token", null, "own-user", "Owned", 3600,
                    List.of("chat:read", "chat:edit", "user:read:follows"));
            stored.getContext().put("client_id", "test-client");
            var backing = mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class);
            when(backing.snapshot()).thenReturn(new com.github.k7t3.tcv.domain.auth.CredentialStore.Snapshot("synthetic", List.of(stored)));
            var scope = new com.github.k7t3.tcv.domain.auth.SessionCredentialStore(backing, stored);
            var token = scope.credential();
            var identity = mock(com.github.twitch4j.auth.providers.TwitchIdentityProvider.class);
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.getAdditionalCredentialInformation(any())).thenAnswer(ignored -> Optional.of(scope.credential()));
            var credentials = mock(CredentialManager.class);
            when(credentials.getIdentityProviderByName("twitch", com.github.twitch4j.auth.providers.TwitchIdentityProvider.class))
                    .thenReturn(Optional.of(identity));
            // SDKのbuilder/Chat本体は実行し、外部へ接続するWebSocketだけを置き換える。
            try (var sockets = mockConstruction(com.github.twitch4j.client.websocket.WebsocketConnection.class)) {
                try (var owner = TwitchClientFactory.create(credentials, token)) {
                    assertThat(owner.client().getHelix()).isNotNull();
                    assertThat(owner.client().getChat().getClass()).isEqualTo(com.github.twitch4j.chat.TwitchChat.class);
                    assertThat(token.getScopes()).containsExactly("chat:read", "chat:edit", "user:read:follows");
                    assertThat(sockets.constructed()).hasSize(1);
                    verify(sockets.constructed().getFirst()).connect();
                }
                verify(sockets.constructed().getFirst(), atLeastOnce()).close();
            }
            System.out.println("productionClientsConstructedAndClosed=true");
        }

        private static void verifyHeadersTimeout() throws Exception {
            var started = new java.util.concurrent.CountDownLatch(1);
            var peerClosed = new java.util.concurrent.CountDownLatch(1);
            var stop = new java.util.concurrent.atomic.AtomicBoolean();
            var accepted = new java.util.concurrent.atomic.AtomicReference<java.net.Socket>();
            try (var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
                 var handler = Executors.newVirtualThreadPerTaskExecutor()) {
                server.setSoTimeout(5000);
                var sending = handler.submit(() -> {
                    try (var socket = server.accept()) {
                        accepted.set(socket);
                        var output = socket.getOutputStream();
                        output.write("HTTP/1.1 200 OK\r\nX-Slow: ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        output.flush();
                        started.countDown();
                        while (!stop.get()) {
                            output.write('a');
                            output.flush();
                            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                        }
                    } catch (java.io.IOException failure) {
                        if (started.getCount() != 0) throw new AssertionError(failure);
                        if (!stop.get()) peerClosed.countDown();
                    }
                });
                try {
                    var token = new com.github.philippheuer.credentialmanager.domain.OAuth2Credential(
                            "twitch", "synthetic-token", null, "own-user", "Test User", 3600, List.of("user:read:follows"));
                    token.getContext().put("client_id", "test-client");
                    var identity = mock(com.github.twitch4j.auth.providers.TwitchIdentityProvider.class);
                    when(identity.getProviderName()).thenReturn("twitch");
                    when(identity.getAdditionalCredentialInformation(any())).thenReturn(Optional.of(token));
                    var credentials = mock(CredentialManager.class);
                    when(credentials.getIdentityProviderByName("twitch", com.github.twitch4j.auth.providers.TwitchIdentityProvider.class))
                            .thenReturn(Optional.of(identity));
                    var scheduler = new ScheduledThreadPoolExecutor(1);
                    try (var http = HelixClientFactory.create(credentials, null, scheduler,
                            new HelixClientFactory.Options("test-client", null,
                                    "http://127.0.0.1:" + server.getLocalPort() + "/helix", 1000))) {
                        assertThatThrownBy(() -> http.client().getGlobalChatBadges("synthetic-token"))
                                .isInstanceOf(RuntimeException.class);
                        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                        assertThat(http.isClosed()).isFalse();

                        assertThat(peerClosed.await(2, TimeUnit.SECONDS))
                                .as("the timed-out connection ends before the HTTP client itself is closed").isTrue();

                        System.out.println("headersTimedOutPhysically=true");
                    } finally {
                        scheduler.shutdownNow();
                        assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                    }
                } finally {
                    stop.set(true);
                    var socket = accepted.get();
                    if (socket != null) socket.close();
                    sending.get(5, TimeUnit.SECONDS);
                }
            }
        }

        private static void verifyPendingWorker() throws Exception {
            var accepted = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            var identity = mock(com.github.twitch4j.auth.providers.TwitchIdentityProvider.class);
            var credentials = mock(CredentialManager.class);
            when(credentials.getIdentityProviderByName("twitch", com.github.twitch4j.auth.providers.TwitchIdentityProvider.class))
                    .thenReturn(Optional.of(identity));
            var scheduler = new ScheduledThreadPoolExecutor(1);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor();
                 var http = HelixClientFactory.create(credentials, null, scheduler,
                         new HelixClientFactory.Options("test-client", null, "http://127.0.0.1:1/helix", 1000), thread -> {
                             accepted.countDown();
                             try {
                                 if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker start was not resumed");
                             } catch (InterruptedException failure) {
                                 Thread.currentThread().interrupt();
                                 throw new AssertionError(failure);
                             }
                             thread.start();
                         })) {
                try {
                    var request = workers.submit(() -> http.client().getGlobalChatBadges("synthetic-token"));
                    assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
                    var closing = workers.submit(http::close);
                    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (!http.isClosed() && System.nanoTime() < deadline) {
                        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    }
                    assertThat(http.isClosed()).isTrue();

                    assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);

                    resume.countDown();
                    assertThatThrownBy(() -> request.get(2, TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class)
                            .hasCauseInstanceOf(java.util.concurrent.CancellationException.class);
                    closing.get(2, TimeUnit.SECONDS);
                    verify(identity, never()).getAdditionalCredentialInformation(any());
                    System.out.println("pendingWorkerReleased=true");
                } finally { resume.countDown(); }
            } finally {
                scheduler.shutdownNow();
                assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }

        private static void verifyFailedStartup(String mode) throws Exception {
            Throwable failure = mode.equals("error") ? new AssertionError("request thread cannot start")
                    : new java.util.concurrent.RejectedExecutionException("request thread cannot start");
            var attempted = new java.util.concurrent.atomic.AtomicInteger();
            var scheduler = new ScheduledThreadPoolExecutor(1);
            try (var http = HelixClientFactory.create(mock(CredentialManager.class), null, scheduler,
                    new HelixClientFactory.Options("test-client", null, "http://127.0.0.1:1/helix", 1000), thread -> {
                        attempted.incrementAndGet();
                        if (failure instanceof Error error) throw error;
                        throw (RuntimeException) failure;
                    })) {
                assertThatThrownBy(() -> http.client().getGlobalChatBadges("synthetic-token")).isSameAs(failure);

                http.close();

                assertThat(attempted.get()).isEqualTo(1);
                assertThat(http.isClosed()).isTrue();
            } finally {
                scheduler.shutdownNow();
                assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
            System.out.println("failedWorkerReleased=" + mode);
        }

        private static void loadEndpoint(String operation) throws Exception {
            var requests = new CopyOnWriteArrayList<String>();
            var authorizations = new CopyOnWriteArrayList<String>();
            var clientIds = new CopyOnWriteArrayList<String>();
            var failures = new CopyOnWriteArrayList<Throwable>();
            try (var handler = Executors.newVirtualThreadPerTaskExecutor()) {
                var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
                try {
                    server.setExecutor(handler);
                    server.createContext("/helix", exchange -> {
                        try (exchange) {
                            requests.add(exchange.getRequestURI().toString());
                            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                            clientIds.add(exchange.getRequestHeaders().getFirst("Client-Id"));
                            var body = responseBody(exchange.getRequestURI().getPath());
                            var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, bytes.length);
                            exchange.getResponseBody().write(bytes);
                        } catch (Throwable failure) { failures.add(failure); }
                    });
                    server.start();
                    var token = new com.github.philippheuer.credentialmanager.domain.OAuth2Credential(
                            "twitch", "synthetic-token", null, "own-user", "Test User", 3600, List.of("user:read:follows"));
                    token.getContext().put("client_id", "test-client");
                    var identity = mock(com.github.twitch4j.auth.providers.TwitchIdentityProvider.class);
                    when(identity.getProviderName()).thenReturn("twitch");
                    when(identity.getAdditionalCredentialInformation(any())).thenReturn(Optional.of(token));
                    var credentials = mock(CredentialManager.class);
                    when(credentials.getIdentityProviderByName("twitch", com.github.twitch4j.auth.providers.TwitchIdentityProvider.class))
                            .thenReturn(Optional.of(identity));
                    var scheduler = new ScheduledThreadPoolExecutor(1);
                    try (var http = HelixClientFactory.create(credentials, null, scheduler,
                            new HelixClientFactory.Options("test-client", null,
                                    "http://127.0.0.1:" + server.getAddress().getPort() + "/helix", 1000))) {
                        var clients = mock(TwitchClients.class);
                        when(clients.getHelix()).thenReturn(http.client());
                        var twitch = mock(Twitch.class);
                        when(twitch.getClient()).thenReturn(clients);
                        when(twitch.getAccessToken()).thenReturn("synthetic-token");
                        when(twitch.getUserId()).thenReturn("own-user");
                        try (var api = new TwitchAPI(twitch)) {
                            var expected = verifyEndpoint(api, operation);
                            assertThat(requests).containsExactlyElementsOf(expected);
                            assertThat(authorizations).containsExactlyElementsOf(java.util.Collections.nCopies(expected.size(), "Bearer synthetic-token"));
                            assertThat(clientIds).containsExactlyElementsOf(java.util.Collections.nCopies(expected.size(), "test-client"));
                        }
                    } finally {
                        scheduler.shutdownNow();
                        assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                    }
                    assertThat(failures).isEmpty();
                    System.out.println("legacyFreeHttpDecoded=" + operation);
                } finally { server.stop(0); }
            }
        }

        private static List<String> verifyEndpoint(TwitchAPI api, String operation) {
            return switch (operation) {
                case "followed" -> {
                    assertThat(api.getFollowChannelOwners()).extracting(b -> List.of(b.getUserId(), b.getUserLogin(), b.getUserName()))
                            .containsExactly(List.of("42", "streamer", "Streamer"));
                    yield List.of("/helix/channels/followed?user_id=own-user&first=100", "/helix/users?id=42");
                }
                case "search" -> {
                    assertThat(api.search("hello world", true))
                            .extracting(c -> c.getBroadcaster().getUserId(), c -> c.isLive(), c -> c.getGameName())
                            .containsExactly(tuple("42", true, "Game"));
                    yield List.of("/helix/search/channels?first=40&live_only=true&query=hello%20world", "/helix/users?id=42");
                }
                case "users" -> {
                    assertThat(api.getBroadcasters(List.of("42"))).extracting(b -> List.of(b.getUserId(), b.getUserLogin(), b.getUserName()))
                            .containsExactly(List.of("42", "streamer", "Streamer"));
                    yield List.of("/helix/users?id=42");
                }
                case "streams" -> {
                    assertThat(api.getStreams(List.of("42"))).extracting(s -> s.userId(), s -> s.title(), s -> s.viewerCount())
                            .containsExactly(tuple("42", "Live", 7));
                    yield List.of("/helix/streams?first=1&user_id=42");
                }
                case "global-badges" -> {
                    assertThat(api.getGlobalBadgeSet()).extracting(b -> b.getSetId(), b -> b.getVersions().getFirst().getId())
                            .containsExactly(tuple("subscriber", "1"));
                    yield List.of("/helix/chat/badges/global");
                }
                case "channel-badges" -> {
                    var broadcaster = new com.github.k7t3.tcv.domain.channel.Broadcaster("42", "streamer", "Streamer", "", "");
                    assertThat(api.getChannelBadgeSet(broadcaster)).extracting(b -> b.getSetId(), b -> b.getVersions().getFirst().getId())
                            .containsExactly(tuple("subscriber", "1"));
                    yield List.of("/helix/chat/badges?broadcaster_id=42");
                }
                case "clips" -> {
                    assertThat(api.getClips(List.of("clip-1"))).extracting(c -> c.getId(), c -> c.getTitle())
                            .containsExactly(tuple("clip-1", "Clip"));
                    yield List.of("/helix/clips?id=clip-1&first=1");
                }
                default -> throw new AssertionError("unknown endpoint: " + operation);
            };
        }

        private static String responseBody(String path) {
            return switch (path) {
                case "/helix/channels/followed" -> """
                    {"data":[{"broadcaster_id":"42","broadcaster_login":"streamer","broadcaster_name":"Streamer","followed_at":"2026-01-01T00:00:00Z"}],"total":1,"pagination":{}}
                    """;
                case "/helix/users" -> """
                    {"data":[{"id":"42","login":"streamer","display_name":"Streamer","profile_image_url":"","offline_image_url":""}]}
                    """;
                case "/helix/search/channels" -> """
                    {"data":[{"id":"42","broadcaster_login":"streamer","display_name":"Streamer","broadcaster_language":"ja","game_name":"Game","is_live":true,"title":"Live"}],"pagination":{}}
                    """;
                case "/helix/streams" -> """
                    {"data":[{"id":"stream-1","user_id":"42","user_login":"streamer","user_name":"Streamer","game_id":"9","game_name":"Game","type":"live","title":"Live","tags":[],"viewer_count":7,"started_at":"2026-01-01T00:00:00Z","language":"ja","thumbnail_url":"","is_mature":false}],"pagination":{}}
                    """;
                case "/helix/chat/badges", "/helix/chat/badges/global" -> """
                    {"data":[{"set_id":"subscriber","versions":[{"id":"1","image_url_1x":"","image_url_2x":"","image_url_4x":"","title":"Subscriber","description":"Subscriber"}]}]}
                    """;
                case "/helix/clips" -> """
                    {"data":[{"id":"clip-1","title":"Clip","broadcaster_id":"42","broadcaster_name":"Streamer","url":"https://clips.twitch.tv/clip-1"}],"pagination":{}}
                    """;
                default -> throw new AssertionError("unexpected request path: " + path);
            };
        }
    }
}
