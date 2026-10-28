package com.github.k7t3.tcv.domain;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.common.util.EventManagerUtils;
import com.github.k7t3.tcv.domain.HelixClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwitchClientFactoryTest {
    @Test
    void assemblingTheUsedModulesDoesNotAuthorizeAnUnusedFollowListener() throws Exception {
        try (var f = new Fixture()) {
            var identity = f.credentials.getIdentityProviderByName("twitch", TwitchIdentityProvider.class).orElseThrow();
            when(identity.getAdditionalCredentialInformation(any()))
                    .thenThrow(new IllegalStateException("unused follow-listener authorization"));
            var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> f.http, context -> f.chat);

            try (var result = factory.open(f.credentials, f.token)) {
                assertThat(result.client().getHelix()).isSameAs(f.helix);
                assertThat(result.client().getChat()).isSameAs(f.chat);
                assertThat(result.client().getEventManager()).isSameAs(f.events);
                verify(identity, never()).getAdditionalCredentialInformation(any());
            }

            verify(f.http).close(); verify(f.chat).close();
            assertThat(f.eventCloses).hasValue(1);
            assertThat(f.pool.isShutdown()).isTrue();
        }
    }

    @Test
    void cancellationRemainsThePrimaryFailureWhenCleanupAlsoFails() throws Exception {
        try (var f = new Fixture()) {
            var cancelled = new AtomicBoolean();
            var chatFailure = new IllegalStateException("chat cleanup failed");
            var httpFailure = new IllegalArgumentException("http cleanup failed");
            doThrow(chatFailure).when(f.chat).close(); doThrow(httpFailure).when(f.http).close();
            try (var clients = mockConstruction(TwitchClients.class, (client, context) -> {
                cancelled.set(true);
            })) {
                var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> f.http, context -> f.chat);
                assertThatThrownBy(() -> factory.open(f.credentials, f.token, cancelled::get))
                        .isInstanceOf(CancellationException.class)
                        .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactlyInAnyOrder(chatFailure, httpFailure));
                verify(f.http).close(); verify(f.chat).close();
                assertThat(f.eventCloses).hasValue(1); assertThat(f.pool.isShutdown()).isTrue();
            }
        }
    }

    @Test
    void aCancelledProductionConstructionDoesNotCreateChat() throws Exception {
        try (var f = new Fixture(); var builders = mockStatic(TwitchChatBuilder.class)) {
            var builder = mock(TwitchChatBuilder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(f.chat);
            builders.when(TwitchChatBuilder::builder).thenReturn(builder);
            assertThatThrownBy(() -> {
                try (var ignored = TwitchClientFactory.create(f.credentials, f.token, () -> true)) { }
            }).isInstanceOf(CancellationException.class);
            builders.verify(TwitchChatBuilder::builder, never());
        }
    }

    @Test
    void anInterruptedCallerDoesNotStartConstructionOrLoseItsInterrupt() throws Exception {
        try (var f = new Fixture()) {
            var allocations = new AtomicInteger();
            var factory = new TwitchClientFactory(() -> { allocations.incrementAndGet(); return f.pool; },
                    () -> f.events, context -> f.http, context -> f.chat);
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> {
                    try (var ignored = factory.open(f.credentials, f.token)) { }
                }).isInstanceOf(CancellationException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(allocations).hasValue(0);
            } finally { Thread.interrupted(); }
        }
    }

    enum ConstructionStage { POOL, EVENTS, HTTP, CHAT, CLIENT }

    @ParameterizedTest
    @EnumSource(ConstructionStage.class)
    void cancellingDuringConstructionReleasesAcquiredResourcesAndStopsTheNextStage(ConstructionStage stage) throws Exception {
        try (var f = new Fixture()) {
            var cancelled = new AtomicBoolean();
            var httpCreations = new AtomicInteger(); var chatCreations = new AtomicInteger();
            try (var clients = mockConstruction(TwitchClients.class, (client, context) -> {
                if (stage == ConstructionStage.CLIENT) cancelled.set(true);
            })) {
                var factory = new TwitchClientFactory(() -> {
                    if (stage == ConstructionStage.POOL) cancelled.set(true);
                    return f.pool;
                }, () -> {
                    if (stage == ConstructionStage.EVENTS) cancelled.set(true);
                    return f.events;
                }, context -> {
                    httpCreations.incrementAndGet();
                    if (stage == ConstructionStage.HTTP) cancelled.set(true);
                    return f.http;
                }, context -> {
                    chatCreations.incrementAndGet();
                    if (stage == ConstructionStage.CHAT) cancelled.set(true);
                    return f.chat;
                });
                assertThatThrownBy(() -> {
                    try (var ignored = factory.open(f.credentials, f.token, cancelled::get)) { }
                }).isInstanceOf(CancellationException.class);
                assertThat(f.pool.isShutdown()).isTrue();
                assertThat(f.eventCloses).hasValue(stage == ConstructionStage.POOL ? 0 : 1);
                var acquiredHttp = stage == ConstructionStage.HTTP || stage == ConstructionStage.CHAT || stage == ConstructionStage.CLIENT;
                var acquiredChat = stage == ConstructionStage.CHAT || stage == ConstructionStage.CLIENT;
                assertThat(httpCreations).hasValue(acquiredHttp ? 1 : 0);
                assertThat(chatCreations).hasValue(acquiredChat ? 1 : 0);
                verify(f.http, times(acquiredHttp ? 1 : 0)).close();
                verify(f.chat, times(acquiredChat ? 1 : 0)).close();
                assertThat(clients.constructed()).hasSize(stage == ConstructionStage.CLIENT ? 1 : 0);
            }
        }
    }

    @Test
    void aCancelledConstructionDoesNotAllocateResources() throws Exception {
        try (var f = new Fixture()) {
            var allocations = new AtomicInteger();
            var factory = new TwitchClientFactory(() -> { allocations.incrementAndGet(); return f.pool; },
                    () -> f.events, context -> f.http, context -> f.chat);
            assertThatThrownBy(() -> {
                try (var ignored = factory.open(f.credentials, f.token, () -> true)) { }
            }).isInstanceOf(CancellationException.class);
            assertThat(allocations).hasValue(0);
            assertThat(f.pool.isShutdown()).isFalse();
            assertThat(f.eventCloses).hasValue(0);
        }
    }

    @Test
    void theProductionFactorySharesCredentialsEventsAndItsOwnedSchedulerWithChat() throws Exception {
        try (var f = new Fixture(); var builders = mockStatic(TwitchChatBuilder.class)) {
            var builder = mock(TwitchChatBuilder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(f.chat);
            builders.when(TwitchChatBuilder::builder).thenReturn(builder);
            var scheduler = org.mockito.ArgumentCaptor.forClass(ScheduledThreadPoolExecutor.class);
            try (var result = TwitchClientFactory.create(f.credentials, f.token)) {
                verify(builder).withCredentialManager(f.credentials);
                verify(builder).withChatAccount(f.token);
                verify(builder).withEventManager(result.client().getEventManager());
                verify(builder).withScheduledThreadPoolExecutor(scheduler.capture());
                assertThat(result.client().getChat()).isSameAs(f.chat);
                assertThat(scheduler.getValue().getCorePoolSize()).isEqualTo(3);
                assertThat(scheduler.getValue().isShutdown()).isFalse();
            }
            verify(f.chat, times(1)).close();
            assertThat(scheduler.getValue().isShutdown()).isTrue();
            assertThat(scheduler.getValue().awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void theClientUsesTheOwnedModulesAndClosesThemOnce() throws Exception {
        try (var f = new Fixture()) {
            var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> f.http, context -> f.chat);
            try (var result = factory.open(f.credentials, f.token)) {
                assertThat(result.client().getHelix()).isSameAs(f.helix);
                assertThat(result.client().getChat()).isSameAs(f.chat);
                assertThat(result.client().getEventManager()).isSameAs(f.events);
                result.client().close(); result.client().close(); result.close();
                verify(f.http, times(1)).close(); verify(f.chat, times(1)).close();
                assertThat(f.pool.isShutdown()).isTrue(); assertThat(f.eventCloses).hasValue(1);
            }
        }
    }

    @Test
    void aChatConstructionFailureReleasesAllEarlierResourcesAndPreservesBothFailures() throws Exception {
        try (var f = new Fixture()) {
            var original = new IllegalStateException("chat creation failed"); var cleanup = new IllegalArgumentException("http close failed");
            doThrow(cleanup).when(f.http).close();
            var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> f.http, context -> { throw original; });
            assertThatThrownBy(() -> factory.open(f.credentials, f.token)).isSameAs(original)
                    .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanup));
            assertThat(f.pool.isShutdown()).isTrue(); assertThat(f.eventCloses).hasValue(1);
            verify(f.chat, never()).close();
        }
    }

    @Test
    void anHttpConstructionFailureDoesNotCreateChatAndReleasesItsOwner() throws Exception {
        try (var f = new Fixture()) {
            var original = new IllegalStateException("http creation failed"); var chats = new AtomicInteger();
            var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> { throw original; }, context -> { chats.incrementAndGet(); return f.chat; });
            assertThatThrownBy(() -> factory.open(f.credentials, f.token)).isSameAs(original);
            assertThat(chats).hasValue(0); assertThat(f.pool.isShutdown()).isTrue(); assertThat(f.eventCloses).hasValue(1);
        }
    }

    @Test
    void aChatCloseFailureStillEndsTheHttpEventsAndScheduler() throws Exception {
        try (var f = new Fixture()) {
            var original = new IllegalStateException("chat close failed"); doThrow(original).when(f.chat).close();
            var factory = new TwitchClientFactory(() -> f.pool, () -> f.events, context -> f.http, context -> f.chat);
            var result = factory.open(f.credentials, f.token);
            assertThatThrownBy(result::close).isSameAs(original);
            result.close(); verify(f.http, times(1)).close(); verify(f.chat, times(1)).close();
            assertThat(f.pool.isShutdown()).isTrue(); assertThat(f.eventCloses).hasValue(1);
        }
    }

    static final class Fixture implements AutoCloseable {
        final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(3);
        final EventManager events = spy(EventManagerUtils.initializeEventManager(SimpleEventHandler.class));
        final AtomicInteger eventCloses = new AtomicInteger();
        final CredentialManager credentials = mock(CredentialManager.class);
        final OAuth2Credential token = new OAuth2Credential("twitch", "test-token", null, "own", "Test", 3600, List.of());
        final HelixClient helix = mock(HelixClient.class);
        final HelixClientFactory.Handle http = mock(HelixClientFactory.Handle.class);
        final TwitchChat chat = mock(TwitchChat.class);
        Fixture() {
            var identity = mock(TwitchIdentityProvider.class);
            when(credentials.getIdentityProviderByName("twitch", TwitchIdentityProvider.class)).thenReturn(Optional.of(identity));
            when(identity.getAdditionalCredentialInformation(any())).thenReturn(Optional.empty());
            when(http.client()).thenReturn(helix);
            doAnswer(call -> { eventCloses.incrementAndGet(); return call.callRealMethod(); }).when(events).close();
        }
        @Override public void close() throws Exception {
            pool.shutdownNow(); if (eventCloses.get() == 0) events.close();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
