package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.chat.TwitchChat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChannelRepositoryLifecycleTest {
    private static final Broadcaster FIRST = owner("1");
    private static final Broadcaster SECOND = owner("2");

    @Test
    void concurrentJoinsShareOneRoomAndOneSdkJoin() throws Exception {
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> {
                started.countDown();
                release.await();
                return null;
            }).when(fixture.chat).joinChannel(FIRST.getUserLogin());
            var first = workers.submit(channel::getOrJoinChatRoom);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(channel::getOrJoinChatRoom);
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(second.get(5, TimeUnit.SECONDS)).isSameAs(first.get(5, TimeUnit.SECONDS));
                verify(fixture.chat).joinChannel(FIRST.getUserLogin());
            } finally {
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void leavingWaitsForAnInProgressJoinAndThenLeavesThatRoom() throws Exception {
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> {
                started.countDown();
                release.await();
                return null;
            }).when(fixture.chat).joinChannel(FIRST.getUserLogin());
            var joining = workers.submit(channel::getOrJoinChatRoom);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var leaving = workers.submit(channel::leaveChat);
                assertThatThrownBy(() -> leaving.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                joining.get(5, TimeUnit.SECONDS);
                leaving.get(5, TimeUnit.SECONDS);
                assertThat(channel.isChatJoined()).isFalse();
                verify(fixture.chat).leaveChannel(FIRST.getUserLogin());
            } finally {
                release.countDown();
                joining.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rejoiningAPersistentChannelWaitsUntilThePreviousLeaveCompletes() throws Exception {
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            channel.setPersistent(true);
            var previous = channel.getOrJoinChatRoom();
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> {
                started.countDown();
                release.await();
                return null;
            }).when(fixture.chat).leaveChannel(FIRST.getUserLogin());
            var leaving = workers.submit(channel::leaveChat);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var joining = workers.submit(channel::getOrJoinChatRoom);
                assertThatThrownBy(() -> joining.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                leaving.get(5, TimeUnit.SECONDS);
                assertThat(joining.get(5, TimeUnit.SECONDS)).isNotSameAs(previous);
                assertThat(channel.isChatJoined()).isTrue();
                verify(fixture.chat, times(2)).joinChannel(FIRST.getUserLogin());
            } finally {
                release.countDown();
                leaving.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clearedChannelsCannotStartAnotherRequestThroughAnOldReference(boolean badges) {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            fixture.repository.clear();

            assertThatThrownBy(() -> {
                if (badges) channel.loadBadgesIfNotLoaded();
                else channel.getOrJoinChatRoom();
            }).isInstanceOf(IllegalStateException.class);
            verify(fixture.chat, never()).joinChannel(anyString());
            verify(fixture.api, never()).getChannelBadgeSet(any());
        }
    }

    @Test
    void readingJoinedStateDoesNotWaitForJoinIo() throws Exception {
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> {
                started.countDown();
                release.await();
                return null;
            }).when(fixture.chat).joinChannel(FIRST.getUserLogin());
            var joining = workers.submit(channel::getOrJoinChatRoom);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(workers.submit(channel::isChatJoined).get(1, TimeUnit.SECONDS)).isFalse();
                assertThat(workers.submit(channel::getJoinedChatRoom).get(1, TimeUnit.SECONDS)).isEmpty();
            } finally {
                release.countDown();
                var joined = joining.get(5, TimeUnit.SECONDS);
                assertThat(channel.getJoinedChatRoom()).contains(joined);
            }
        }
    }

    @Test
    void concurrentBadgeLoadsShareOneApiRequest() throws Exception {
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(fixture.api.getChannelBadgeSet(FIRST)).thenAnswer(invocation -> {
                started.countDown();
                release.await();
                return List.of();
            });
            var first = workers.submit(channel::loadBadgesIfNotLoaded);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(channel::loadBadgesIfNotLoaded);
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                verify(fixture.api).getChannelBadgeSet(FIRST);
            } finally {
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void channelIdentityDoesNotChangeWhenItsPersistenceOrChatStateChanges() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var channel = fixture.repository.getOrLoadChannel(FIRST);
            var channels = new HashSet<>(List.of(channel));

            channel.setPersistent(true);
            channel.getOrJoinChatRoom();

            assertThat(channels.contains(channel)).isTrue();
            fixture.repository.clear();
            fixture.repository.loadAllRelatedChannels();
            var replacement = fixture.repository.getOrLoadChannel(FIRST);
            replacement.setPersistent(true);
            assertThat(replacement).isNotEqualTo(channel);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedRegistrationAlsoRollsBackAStreamMonitorThatMightAlreadyBeRegistered(boolean batch) {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            when(fixture.api.getBroadcasters(anyList())).thenReturn(List.of(FIRST));
            var failure = new IllegalStateException("monitor failed after registration");
            when(fixture.api.enableStreamEventListener(FIRST)).thenThrow(failure).thenReturn(true);

            assertThatThrownBy(() -> {
                if (batch) fixture.repository.getOrLoadChannels(List.of(FIRST.getUserId()));
                else fixture.repository.getOrLoadChannel(FIRST);
            }).isSameAs(failure);

            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();
            verify(fixture.api).disableStreamEventListener(FIRST);
            if (batch) assertThat(fixture.repository.getOrLoadChannels(List.of(FIRST.getUserId()))).hasSize(1);
            else assertThat(fixture.repository.getOrLoadChannel(FIRST)).isNotNull();
        }
    }

    @Test
    void anInitialMonitorFailureRollsBackAllChannelsAndPreservesCleanupFailures() {
        try (var fixture = new Fixture()) {
            when(fixture.api.getFollowChannelOwners()).thenReturn(List.of(FIRST, SECOND));
            var failure = new IllegalStateException("monitor failed");
            var cleanup = new IllegalStateException("monitor cleanup failed");
            when(fixture.api.enableStreamEventListener(SECOND)).thenThrow(failure).thenReturn(true);
            when(fixture.api.disableStreamEventListener(FIRST)).thenThrow(cleanup);

            assertThatThrownBy(fixture.repository::loadAllRelatedChannels).isSameAs(failure);

            assertThat(failure.getSuppressed()).containsExactly(cleanup);
            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();
            verify(fixture.api).disableStreamEventListener(FIRST);
            verify(fixture.api).disableStreamEventListener(SECOND);
            fixture.subscriptions.forEach(subscription -> verify(subscription).dispose());
            doReturn(true).when(fixture.api).disableStreamEventListener(FIRST);
            fixture.repository.loadAllRelatedChannels();
            assertThat(fixture.repository.getOrLoadChannels()).hasSize(2);
        }
    }

    @Test
    void lookingUpAlreadyCachedChannelsDoesNotMakeAnotherApiRequest() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var first = fixture.repository.getOrLoadChannel(FIRST);
            clearInvocations(fixture.api);

            assertThat(fixture.repository.getOrLoadChannels(List.of(FIRST.getUserId()))).containsExactly(first);

            verifyNoInteractions(fixture.api);
        }
    }

    @Test
    void failedInitialFetchReleasesSubscriptionsAndCanBeRetried() {
        try (var fixture = new Fixture()) {
            var failure = new IllegalStateException("follow request failed");
            when(fixture.api.getFollowChannelOwners()).thenThrow(failure).thenReturn(List.of(FIRST));

            assertThatThrownBy(fixture.repository::loadAllRelatedChannels).isSameAs(failure);
            var previousSubscriptions = List.copyOf(fixture.subscriptions);
            previousSubscriptions.forEach(subscription -> verify(subscription).dispose());
            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();

            fixture.repository.loadAllRelatedChannels();

            assertThat(fixture.repository.getOrLoadChannels()).extracting(TwitchChannel::getBroadcaster).containsExactly(FIRST);
            verify(fixture.api, times(2)).getFollowChannelOwners();
            assertThat(fixture.subscriptions).hasSize(previousSubscriptions.size() * 2);
        }
    }

    @Test
    void clearingSeveralJoinedTemporaryChannelsDoesNotMutateTheIteration() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var first = fixture.repository.getOrLoadChannel(FIRST);
            var second = fixture.repository.getOrLoadChannel(SECOND);
            first.getOrJoinChatRoom();
            second.getOrJoinChatRoom();

            assertThatCode(fixture.repository::clear).doesNotThrowAnyException();

            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();
            assertThat(first.isChatJoined()).isFalse();
            assertThat(second.isChatJoined()).isFalse();
            verify(fixture.chat).leaveChannel(FIRST.getUserLogin());
            verify(fixture.chat).leaveChannel(SECOND.getUserLogin());
            verify(fixture.api).disableStreamEventListener(FIRST);
            verify(fixture.api).disableStreamEventListener(SECOND);
            fixture.subscriptions.forEach(subscription -> verify(subscription).dispose());
        }
    }

    @Test
    void channelCollectionsAreImmutableSnapshots() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var first = fixture.repository.getOrLoadChannel(FIRST);
            var snapshot = fixture.repository.getOrLoadChannels();

            fixture.repository.getOrLoadChannel(SECOND);

            assertThat(snapshot).containsExactly(first);
            assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void clearingAllowsTheSameRepositoryToLoadAndRegisterAgain() {
        try (var fixture = new Fixture()) {
            when(fixture.api.getFollowChannelOwners()).thenReturn(List.of(FIRST));
            fixture.repository.loadAllRelatedChannels();
            var previous = fixture.repository.getOrLoadChannels().iterator().next();
            fixture.repository.clear();

            fixture.repository.loadAllRelatedChannels();

            assertThat(fixture.repository.getOrLoadChannels()).hasSize(1);
            assertThat(fixture.repository.getOrLoadChannels().iterator().next()).isNotSameAs(previous);
            verify(fixture.api, times(2)).getFollowChannelOwners();
            assertThat(fixture.subscriptions).hasSize(30);
        }
    }

    @Test
    void failedMonitorRegistrationDoesNotLeaveACachedChannelThatCannotRetry() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var failure = new IllegalStateException("monitor registration failed");
            when(fixture.api.enableStreamEventListener(FIRST)).thenThrow(failure).thenReturn(true);

            assertThatThrownBy(() -> fixture.repository.getOrLoadChannel(FIRST)).isSameAs(failure);
            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();

            var loaded = fixture.repository.getOrLoadChannel(FIRST);

            assertThat(fixture.repository.getOrLoadChannels()).containsExactly(loaded);
            verify(fixture.api, times(2)).enableStreamEventListener(FIRST);
        }
    }

    @Test
    void releasingAnOldInstanceCannotRemoveItsReplacementWithTheSameId() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var previous = fixture.repository.getOrLoadChannel(FIRST);
            fixture.repository.releaseChannel(previous);
            var current = fixture.repository.getOrLoadChannel(FIRST);
            assertThat(current).isNotSameAs(previous);

            fixture.repository.releaseChannel(previous);

            assertThat(fixture.repository.getOrLoadChannels()).containsExactly(current);
            verify(fixture.api).disableStreamEventListener(FIRST);
        }
    }

    @Test
    void oneLeaveFailureDoesNotKeepTheOtherChannelsAndSubscriptionsAlive() {
        try (var fixture = new Fixture()) {
            fixture.repository.loadAllRelatedChannels();
            var first = fixture.repository.getOrLoadChannel(FIRST);
            var second = fixture.repository.getOrLoadChannel(SECOND);
            first.getOrJoinChatRoom();
            second.getOrJoinChatRoom();
            var failure = new IllegalStateException("leave failed");
            doThrow(failure).when(fixture.chat).leaveChannel(FIRST.getUserLogin());

            assertThatThrownBy(fixture.repository::clear).isSameAs(failure);

            assertThat(fixture.repository.getOrLoadChannels()).isEmpty();
            assertThat(first.isChatJoined()).isFalse();
            assertThat(second.isChatJoined()).isFalse();
            verify(fixture.chat).leaveChannel(SECOND.getUserLogin());
            verify(fixture.api).disableStreamEventListener(FIRST);
            verify(fixture.api).disableStreamEventListener(SECOND);
            fixture.subscriptions.forEach(subscription -> verify(subscription).dispose());
        }
    }

    private static Broadcaster owner(String id) {
        return new Broadcaster(id, "channel" + id, "Channel " + id, "", "");
    }

    private static final class Fixture implements AutoCloseable {
        final Twitch twitch = mock(Twitch.class);
        final TwitchClients client = mock(TwitchClients.class, RETURNS_DEEP_STUBS);
        final TwitchChat chat = mock(TwitchChat.class, RETURNS_DEEP_STUBS);
        final TwitchAPI api = mock(TwitchAPI.class);
        final List<IDisposable> subscriptions = new ArrayList<>();
        final ChannelRepository repository;

        Fixture() {
            when(twitch.getClient()).thenReturn(client);
            when(twitch.getChat()).thenReturn(chat);
            when(twitch.getTwitchAPI()).thenReturn(api);
            when(api.getFollowChannelOwners()).thenReturn(List.of());
            when(api.getStreams(anyList())).thenReturn(List.of());
            when(api.getStream(anyString())).thenReturn(Optional.empty());
            when(client.getEventManager().onEvent(any(), any())).thenAnswer(invocation -> subscription());
            when(chat.getEventManager().onEvent(any(), any())).thenAnswer(invocation -> subscription());
            repository = new ChannelRepository(twitch, mock(EventPublishers.class));
        }

        private IDisposable subscription() {
            var subscription = mock(IEventSubscription.class);
            subscriptions.add(subscription);
            return subscription;
        }

        @Override
        public void close() {
            repository.clear();
        }
    }
}
