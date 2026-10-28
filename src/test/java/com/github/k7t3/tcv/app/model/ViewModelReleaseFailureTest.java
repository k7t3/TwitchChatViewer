package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.utils.notifications.NotificationCenter;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class ViewModelReleaseFailureTest {

    @Test
    void lifecycleDelegationAttemptsEveryDisconnectEvenWhenOneFails() {
        var center = mock(NotificationCenter.class);
        var registered = new ArrayList<NotificationObserver>();
        var removed = new ArrayList<NotificationObserver>();
        var failure = new IllegalStateException("lifecycle disconnect failed");
        doAnswer(invocation -> { registered.add(invocation.getArgument(1)); return null; })
                .when(center).subscribe(anyString(), any(NotificationObserver.class));
        doAnswer(invocation -> {
            removed.add(invocation.getArgument(0));
            if (removed.size() == 1) throw failure;
            return null;
        }).when(center).unsubscribe(any(NotificationObserver.class));
        try (var mvvm = mockStatic(MvvmFX.class)) {
            mvvm.when(MvvmFX::getNotificationCenter).thenReturn(center);
            var model = new AbstractViewModel() { };
            try {
                assertThatThrownBy(model::delegateLifecycleToOwner).isSameAs(failure);

                assertThat(removed).containsExactlyElementsOf(registered);
                assertThat(model.isClosed()).isFalse();
                var events = java.util.List.of(new com.github.k7t3.tcv.app.event.LoginEvent(),
                        new com.github.k7t3.tcv.app.event.LogoutEvent(), new com.github.k7t3.tcv.app.event.ShutdownEvent());
                for (var index = 0; index < registered.size(); index++) {
                    var event = events.get(index);
                    registered.get(index).receivedNotification(event.getClass().getName(), event);
                }
                assertThat(model.isClosed()).isFalse();
            } finally {
                model.close();
            }
        }
    }

    @Test
    void notificationUnsubscribeFailureDoesNotSkipOtherRegistrationsOrResources() {
        var center = mock(NotificationCenter.class);
        var registered = new ArrayList<NotificationObserver>();
        var removed = new ArrayList<NotificationObserver>();
        var released = new ArrayList<String>();
        var first = new IllegalStateException("first unsubscribe failed");
        var second = new IllegalStateException("second unsubscribe failed");
        doAnswer(invocation -> { registered.add(invocation.getArgument(1)); return null; })
                .when(center).subscribe(anyString(), any(NotificationObserver.class));
        doAnswer(invocation -> {
            removed.add(invocation.getArgument(0));
            if (removed.size() == 1) throw first;
            if (removed.size() == 2) throw second;
            return null;
        }).when(center).unsubscribe(any(NotificationObserver.class));
        FlowableSubscriber<?> subscriber = mock(FlowableSubscriber.class);
        doAnswer(invocation -> { released.add("flow"); return null; }).when(subscriber).cancel();
        try (var mvvm = mockStatic(MvvmFX.class)) {
            mvvm.when(MvvmFX::getNotificationCenter).thenReturn(center);
            var model = new AbstractViewModel() {
                @Override protected void onSubscribeEvents(EventSubscribers source, SessionSubscriptions subscriptions) { subscriptions.own(subscriber); }
                @Override protected void onClose() { released.add("model"); }
            };
            try {
                model.subscribeEvents(mock(EventSubscribers.class));

                assertThatThrownBy(model::close).isSameAs(first);

                assertThat(registered).hasSize(3);
                assertThat(removed).containsExactlyElementsOf(registered);
                assertThat(released).containsExactly("flow", "model");
                assertThat(first.getSuppressed()).containsExactly(second);
                model.close();
                assertThat(removed).containsExactlyElementsOf(registered);
                assertThat(released).containsExactly("flow", "model");
            } finally {
                model.close();
            }
        }
    }

    @ParameterizedTest(name = "subscription failure preserves its cause, error={0}")
    @ValueSource(booleans = {false, true})
    void partialSubscriptionFailureKeepsItsCauseWhenCleanupAlsoFails(boolean error) {
        var released = new ArrayList<String>();
        var attempts = new AtomicInteger();
        var registration = error ? new AssertionError("registration failed") : new IllegalStateException("registration failed");
        var first = new IllegalStateException("first cancellation failed");
        var second = new IllegalStateException("second cancellation failed");
        FlowableSubscriber<?> firstSubscriber = mock(FlowableSubscriber.class);
        FlowableSubscriber<?> secondSubscriber = mock(FlowableSubscriber.class);
        doAnswer(invocation -> { released.add("first"); throw first; }).when(firstSubscriber).cancel();
        doAnswer(invocation -> { released.add("second"); throw second; }).when(secondSubscriber).cancel();
        var model = new AbstractViewModel() {
            @Override protected void onSubscribeEvents(EventSubscribers source, SessionSubscriptions subscriptions) {
                if (attempts.incrementAndGet() > 1) return;
                subscriptions.own(firstSubscriber);
                subscriptions.own(secondSubscriber);
                if (registration instanceof RuntimeException failure) throw failure;
                throw (Error) registration;
            }
        };
        try {
            var source = mock(EventSubscribers.class);

            assertThatThrownBy(() -> model.subscribeEvents(source)).isSameAs(registration);

            assertThat(released).containsExactly("first", "second");
            assertThat(registration.getSuppressed()).containsExactly(first);
            assertThat(first.getSuppressed()).containsExactly(second);
            model.subscribeEvents(source);
            assertThat(attempts.get()).isEqualTo(2);
        } finally {
            model.close();
        }
    }

    @ParameterizedTest(name = "release all resources after cancellation failure, logout={0}")
    @ValueSource(booleans = {false, true})
    void cancellationFailureDoesNotSkipOtherSubscriptionsOrModelResources(boolean logout) {
        var released = new ArrayList<String>();
        var first = new IllegalStateException("first cancellation failed");
        var second = new IllegalStateException("second cancellation failed");
        var cleanup = new IllegalStateException("model cleanup failed");
        FlowableSubscriber<?> firstSubscriber = mock(FlowableSubscriber.class);
        FlowableSubscriber<?> secondSubscriber = mock(FlowableSubscriber.class);
        doAnswer(invocation -> { released.add("first"); throw first; }).when(firstSubscriber).cancel();
        doAnswer(invocation -> { released.add("second"); throw second; }).when(secondSubscriber).cancel();
        var model = new AbstractViewModel() {
            @Override protected void onSubscribeEvents(EventSubscribers source, SessionSubscriptions subscriptions) {
                subscriptions.own(firstSubscriber);
                subscriptions.own(secondSubscriber);
            }

            @Override protected void onLoggedOut() {
                released.add("logout");
                if (logout) throw cleanup;
            }

            @Override protected void onClose() {
                released.add("close");
                if (!logout) throw cleanup;
            }
        };
        try {
            model.subscribeEvents(mock(EventSubscribers.class));

            assertThatThrownBy(() -> {
                if (logout) model.onLogout();
                else model.close();
            }).isSameAs(first);

            assertThat(released).containsExactly("first", "second", logout ? "logout" : "close");
            assertThat(first.getSuppressed()).containsExactly(second, cleanup);
            model.close();
            model.close();
            assertThat(released).containsExactlyElementsOf(logout
                    ? java.util.List.of("first", "second", "logout", "close")
                    : java.util.List.of("first", "second", "close"));
        } finally {
            model.close();
        }
    }
}
