package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.image.ImageLoader;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ViewModelSubscriptionTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewModels")
    void closeCancelsOwnedSubscriptions(String name, Supplier<AbstractViewModel> factory, int count) {
        verifyCancellation(factory, count, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewModels")
    void logoutCancelsOwnedSubscriptions(String name, Supplier<AbstractViewModel> factory, int count) {
        verifyCancellation(factory, count, true);
    }

    private static void verifyCancellation(Supplier<AbstractViewModel> factory, int count, boolean logout) {
        var repository = factory.get();
        var events = mock(EventSubscribers.class);
        var subscriptions = new ArrayList<Flow.Subscription>();
        Answer<Void> register = invocation -> {
            Flow.Subscriber<?> subscriber = invocation.getArgument(0);
            var subscription = mock(Flow.Subscription.class);
            subscriptions.add(subscription);
            subscriber.onSubscribe(subscription);
            return null;
        };
        doAnswer(register).when(events).subscribeChannelEvent(any());
        doAnswer(register).when(events).subscribeChatEvent(any());
        doAnswer(register).when(events).subscribeMessageEvent(any());

        repository.subscribeEvents(events);
        if (logout) repository.onLogout();
        repository.close();
        repository.close();

        assertThat(subscriptions).hasSize(count);
        subscriptions.forEach(subscription -> verify(subscription).cancel());
    }

    private static AppHelper helper() {
        var helper = mock(AppHelper.class);
        when(helper.getExecutor()).thenReturn(Runnable::run);
        return helper;
    }

    private static Stream<Arguments> viewModels() {
        return Stream.of(
                Arguments.of("channels", (Supplier<AbstractViewModel>) () ->
                        new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), Runnable::run, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); }), 3),
                Arguments.of("clips", (Supplier<AbstractViewModel>) () -> new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); }), 1),
                Arguments.of("notifications", (Supplier<AbstractViewModel>) () ->
                        new LiveStateNotificator(mock(ChannelViewModelRepository.class), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); }), 2),
                Arguments.of("chat", (Supplier<AbstractViewModel>) () -> MainModelFactory.createChat(helper(), mock(ImageLoader.class)), 8)
        );
    }
}
