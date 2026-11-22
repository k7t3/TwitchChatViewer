package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.roomstate.EmoteOnlyEvent;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventProviderLifecycleTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "", "");

    @ParameterizedTest(name = "older registration completion preserves replacement, chat={0}, failure={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void olderRegistrationCompletionCannotReplaceOrRetireANewerGeneration(boolean chat, boolean failure) {
        var fixture = new Fixture(chat);
        var boundary = chat ? EmoteOnlyEvent.class : StreamStateEvent.Offline.class;
        var lateFailure = new IllegalStateException("retired subscription disposal failed");
        doAnswer(invocation -> {
            var subscription = fixture.register(invocation.getArgument(0), invocation.getArgument(1));
            if (invocation.getArgument(0) == boundary) {
                fixture.clean.run();
                fixture.listenAlwaysSucceeds();
                fixture.listen.run();
                fixture.addCurrent();
                if (failure) doThrow(lateFailure).when(subscription).dispose();
            }
            return subscription;
        }).when(fixture.manager).onEvent(any(), any());
        try {
            if (failure) assertThatThrownBy(fixture.listen::run).isSameAs(lateFailure);
            else assertThatCode(fixture.listen::run).doesNotThrowAnyException();

            var boundaryIndex = chat ? 4 : 1;
            assertThat(fixture.registrations).hasSize(chat ? 15 : 7);
            fixture.registrations.subList(0, boundaryIndex + 1)
                    .forEach(registration -> verify(registration.subscription()).dispose());
            fixture.registrations.subList(boundaryIndex + 1, fixture.registrations.size())
                    .forEach(registration -> verify(registration.subscription(), never()).dispose());
            assertThatThrownBy(fixture.listen::run).isInstanceOf(IllegalStateException.class)
                    .hasMessage(chat ? "chat events are already registered" : "channel events are already registered");
            fixture.registrations.get(boundaryIndex).callback().accept(fixture.event());
            verifyNoInteractions(fixture.publishers);
            fixture.callback().accept(fixture.event());
            if (chat) verify(fixture.publishers).submit(any(ChatRoomStateUpdatedEvent.class));
            else verify(fixture.publishers).submit(any(ChannelOfflineEvent.class));
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest(name = "concurrent replacement during SDK cleanup, chat={0}")
    @ValueSource(booleans = {false, true})
    void replacementCanRegisterWhileOldSdkDisposalWaitsForIt(boolean chat) throws Exception {
        var fixture = new Fixture(chat);
        var replacement = new AtomicReference<Future<?>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.listen.run();
            var retired = List.copyOf(fixture.registrations);
            var previous = fixture.callback();
            doAnswer(invocation -> {
                var next = worker.submit(fixture.listen);
                replacement.set(next);
                try { next.get(5, TimeUnit.SECONDS); }
                catch (TimeoutException failure) { throw new AssertionError("replacement blocked by SDK cleanup", failure); }
                return null;
            }).when(retired.getFirst().subscription()).dispose();
            try {
                assertThatCode(fixture.clean::run).doesNotThrowAnyException();

                retired.forEach(registration -> verify(registration.subscription()).dispose());
                assertThat(fixture.registrations).hasSize(chat ? 20 : 10);
                fixture.registrations.subList(retired.size(), fixture.registrations.size())
                        .forEach(registration -> verify(registration.subscription(), never()).dispose());
                fixture.addCurrent();
                previous.accept(fixture.event());
                verifyNoInteractions(fixture.publishers);
                fixture.callback().accept(fixture.event());
                if (chat) verify(fixture.publishers).submit(any(ChatRoomStateUpdatedEvent.class));
                else verify(fixture.publishers).submit(any(ChannelOfflineEvent.class));
            } finally {
                if (replacement.get() != null) replacement.get().get(5, TimeUnit.SECONDS);
                fixture.clean.run();
            }
        }
    }

    @ParameterizedTest(name = "concurrent cleanup during SDK registration, chat={0}")
    @ValueSource(booleans = {false, true})
    void cleanupCanCompleteWhileAnSdkRegistrationWaitsForIt(boolean chat) {
        var fixture = new Fixture(chat);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            doAnswer(invocation -> {
                var subscription = fixture.register(invocation.getArgument(0), invocation.getArgument(1));
                if (fixture.registrations.size() == 1) worker.submit(fixture.clean).get(5, TimeUnit.SECONDS);
                return subscription;
            }).when(fixture.manager).onEvent(any(), any());
            try {
                assertThatCode(fixture.listen::run).doesNotThrowAnyException();

                assertThat(fixture.registrations).hasSize(1);
                verify(fixture.registrations.getFirst().subscription()).dispose();
                fixture.listenAlwaysSucceeds();
                assertThatCode(fixture.listen::run).doesNotThrowAnyException();
            } finally {
                fixture.clean.run();
            }
        }
    }

    @ParameterizedTest(name = "cleanup during SDK registration, chat={0}")
    @ValueSource(booleans = {false, true})
    void cleanupDuringRegistrationDisposesLateSubscriptionsAndAllowsANewGeneration(boolean chat) {
        var fixture = new Fixture(chat);
        var boundary = chat ? EmoteOnlyEvent.class : StreamStateEvent.Offline.class;
        doAnswer(invocation -> {
            var subscription = fixture.register(invocation.getArgument(0), invocation.getArgument(1));
            if (invocation.getArgument(0) == boundary) fixture.clean.run();
            return subscription;
        }).when(fixture.manager).onEvent(any(), any());
        try {
            assertThatCode(fixture.listen::run).doesNotThrowAnyException();

            var retired = List.copyOf(fixture.registrations);
            assertThat(retired).hasSize(chat ? 5 : 2);
            retired.forEach(registration -> verify(registration.subscription()).dispose());
            var previous = fixture.callback();
            fixture.listenAlwaysSucceeds();
            fixture.listen.run();
            fixture.addCurrent();

            previous.accept(fixture.event());

            verifyNoInteractions(fixture.publishers);
            fixture.callback().accept(fixture.event());
            if (chat) verify(fixture.publishers).submit(any(ChatRoomStateUpdatedEvent.class));
            else verify(fixture.publishers).submit(any(ChannelOfflineEvent.class));
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void registrationFailureKeepsItsCauseWhenAnEarlierSubscriptionCannotBeDisposed(boolean chat) {
        var fixture = new Fixture(chat);
        var failure = new IllegalStateException("registration failed");
        var cleanup = new IllegalStateException("cleanup failed");
        doAnswer(invocation -> {
            if (fixture.registrations.size() == 2) throw failure;
            var subscription = fixture.register(invocation.getArgument(0), invocation.getArgument(1));
            if (fixture.registrations.size() == 1) doThrow(cleanup).when(subscription).dispose();
            return subscription;
        }).when(fixture.manager).onEvent(any(), any());
        try {
            assertThatThrownBy(fixture.listen::run).isSameAs(failure);
            assertThat(failure.getSuppressed()).containsExactly(cleanup);
            fixture.registrations.forEach(registration -> verify(registration.subscription()).dispose());
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partialRegistrationFailureDisposesEarlierSubscriptionsAndAllowsRetry(boolean chat) {
        var fixture = new Fixture(chat);
        var failure = new IllegalStateException("registration failed");
        doAnswer(invocation -> {
            if (fixture.registrations.size() == 2) throw failure;
            return fixture.register(invocation.getArgument(0), invocation.getArgument(1));
        }).when(fixture.manager).onEvent(any(), any());
        try {
            assertThatThrownBy(fixture.listen::run).isSameAs(failure);
            fixture.registrations.forEach(registration -> verify(registration.subscription()).dispose());
            fixture.listenAlwaysSucceeds();

            assertThatCode(fixture.listen::run).doesNotThrowAnyException();

            assertThat(fixture.registrations).hasSize(chat ? 12 : 7);
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleanupAttemptsEverySubscriptionAndCanBeRegisteredAgainAfterFailure(boolean chat) {
        var fixture = new Fixture(chat);
        fixture.listen.run();
        var previous = List.copyOf(fixture.registrations);
        var first = new IllegalStateException("first cleanup failed");
        var second = new IllegalStateException("second cleanup failed");
        doThrow(first).when(previous.get(0).subscription()).dispose();
        doThrow(second).when(previous.get(1).subscription()).dispose();
        try {
            assertThatThrownBy(fixture.clean::run).isSameAs(first);
            assertThat(first.getSuppressed()).containsExactly(second);
            previous.forEach(registration -> verify(registration.subscription()).dispose());
            fixture.clean.run();

            assertThatCode(fixture.listen::run).doesNotThrowAnyException();
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queuedCallbacksFromAPreviousRegistrationCannotPublishIntoANewSession(boolean chat) {
        var fixture = new Fixture(chat);
        try {
            fixture.listen.run();
            fixture.addCurrent();
            var previous = fixture.callback();
            fixture.clean.run();
            fixture.listen.run();
            fixture.addCurrent();

            previous.accept(fixture.event());

            verifyNoInteractions(fixture.publishers);
            fixture.callback().accept(fixture.event());
            if (chat) verify(fixture.publishers).submit(any(ChatRoomStateUpdatedEvent.class));
            else verify(fixture.publishers).submit(any(ChannelOfflineEvent.class));
        } finally {
            fixture.clean.run();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releasingAnOldRegistrationCannotRemoveItsReplacement(boolean chat) {
        var fixture = new Fixture(chat);
        try {
            fixture.listen.run();
            fixture.addCurrent();
            var previousRoom = fixture.room;
            var previousChannel = fixture.channel;
            fixture.addCurrent();

            if (chat) fixture.chats.onLeft(previousRoom);
            else fixture.channels.remove(previousChannel);
            fixture.callback().accept(fixture.event());

            if (chat) verify(fixture.publishers).submit(any(ChatRoomStateUpdatedEvent.class));
            else verify(fixture.publishers).submit(any(ChannelOfflineEvent.class));
        } finally {
            fixture.clean.run();
        }
    }

    private record Registration(Class<?> type, Consumer<Object> callback, IEventSubscription subscription) {}

    private static final class Fixture {
        final EventManager manager = mock(EventManager.class);
        final EventPublishers publishers = mock(EventPublishers.class);
        final List<Registration> registrations = new ArrayList<>();
        final TwitchChannelEventProvider channels;
        final ChatRoomEventProvider chats;
        final Runnable listen;
        final Runnable clean;
        final boolean chat;
        TwitchChannel channel;
        ChatRoom room;

        Fixture(boolean chat) {
            this.chat = chat;
            var twitch = mock(Twitch.class);
            var client = mock(TwitchClients.class);
            var sdkChat = mock(TwitchChat.class);
            when(twitch.getClient()).thenReturn(client);
            when(twitch.getChat()).thenReturn(sdkChat);
            when(client.getEventManager()).thenReturn(manager);
            when(sdkChat.getEventManager()).thenReturn(manager);
            channels = new TwitchChannelEventProvider(twitch, publishers);
            chats = new ChatRoomEventProvider(twitch, publishers);
            listen = chat ? chats::listen : channels::listen;
            clean = chat ? chats::clean : channels::clean;
            listenAlwaysSucceeds();
        }

        void listenAlwaysSucceeds() {
            doAnswer(invocation -> register(invocation.getArgument(0), invocation.getArgument(1)))
                    .when(manager).onEvent(any(), any());
        }

        IEventSubscription register(Class<?> type, Consumer<Object> callback) {
            var subscription = mock(IEventSubscription.class);
            registrations.add(new Registration(type, callback, subscription));
            return subscription;
        }

        void addCurrent() {
            if (chat) {
                room = mock(ChatRoom.class);
                when(room.getBroadcaster()).thenReturn(OWNER);
                chats.onJoined(room);
            } else {
                channel = mock(TwitchChannel.class);
                when(channel.getBroadcaster()).thenReturn(OWNER);
                channels.add(channel);
            }
        }

        Consumer<Object> callback() {
            var type = chat ? EmoteOnlyEvent.class : StreamStateEvent.Offline.class;
            return registrations.stream().filter(registration -> registration.type() == type)
                    .reduce((previous, current) -> current).orElseThrow().callback();
        }

        Object event() {
            if (chat) {
                var event = mock(EmoteOnlyEvent.class, RETURNS_DEEP_STUBS);
                when(event.getChannel().getId()).thenReturn(OWNER.getUserId());
                when(event.isActive()).thenReturn(true);
                return event;
            }
            return new StreamStateEvent.Offline(OWNER.getUserId());
        }
    }
}
