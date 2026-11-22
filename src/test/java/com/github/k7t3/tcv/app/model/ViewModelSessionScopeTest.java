package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.reactive.AbstractSubscriber;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.utils.notifications.DefaultNotificationCenter;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ViewModelSessionScopeTest {
    @Test
    void retiredScopeCannotAdoptNewResourcesAfterTheNextLogin() {
        var model = new Probe();
        try {
            var source = mock(EventSubscribers.class);
            model.subscribeEvents(source);
            var retired = model.scopes.getFirst();
            model.onLogout();
            model.subscribeEvents(source);
            var late = new RecordingSubscriber();
            var lateLink = mock(Flow.Subscription.class);

            retired.own(late).onSubscribe(lateLink);
            late.onNext("retired");
            retired.subscribe(ScopeEvent.class, event -> model.notifications.add("retired"));
            model.subscribers.get(1).onNext("current");
            model.publish(new ScopeEvent("current"));

            assertThat(late.items).isEmpty();
            verify(lateLink).cancel();
            assertThat(model.subscribers.get(1).items).containsExactly("current");
            assertThat(model.notifications).containsExactly("1:current");
            verify(model.links.getFirst()).cancel();
            verify(model.links.get(1), never()).cancel();
        } finally {
            model.close();
        }
        verify(model.links.get(1)).cancel();
    }

    @Test
    void olderRegistrationFailureCannotCancelOrReplaceANewerSession() {
        var model = new Probe();
        var source = mock(EventSubscribers.class);
        var failure = new IllegalStateException("old registration failed");
        model.registering = scope -> {
            if (model.scopes.size() != 1) return;
            model.onLogout();
            model.subscribeEvents(source);
            throw failure;
        };
        try {
            assertThatThrownBy(() -> model.subscribeEvents(source)).isSameAs(failure);

            model.subscribeEvents(source);
            model.publish(new ScopeEvent("new session"));
            assertThat(model.scopes).hasSize(2);
            assertThat(model.notifications).containsExactly("1:new session");
            verify(model.links.getFirst()).cancel();
            verify(model.links.get(1), never()).cancel();
        } finally {
            model.close();
        }
        verify(model.links.get(1)).cancel();
    }

    @Test
    void logoutFailureStillRetiresNotificationsAndFlowBeforeTheNextSession() {
        var center = spy(new DefaultNotificationCenter());
        var failure = new IllegalStateException("notification cleanup failed");
        var cancellation = new IllegalStateException("flow cleanup failed");
        try (var mvvm = mockStatic(MvvmFX.class)) {
            mvvm.when(MvvmFX::getNotificationCenter).thenReturn(center);
            var model = new Probe();
            try {
                var source = mock(EventSubscribers.class);
                model.subscribeEvents(source);
                var observer = ArgumentCaptor.forClass(NotificationObserver.class);
                verify(center).subscribe(eq(ScopeEvent.class.getName()), observer.capture());
                doThrow(failure).when(center).unsubscribe(observer.getValue());
                doThrow(cancellation).when(model.links.getFirst()).cancel();

                assertThatThrownBy(model::onLogout).isSameAs(failure);

                assertThat(failure.getSuppressed()).containsExactly(cancellation);
                assertThat(model.logouts).isEqualTo(1);
                model.publish(new ScopeEvent("retired"));
                assertThat(model.notifications).isEmpty();
                model.subscribeEvents(source);
                model.publish(new ScopeEvent("current"));
                assertThat(model.notifications).containsExactly("1:current");
                verify(model.links.getFirst()).cancel();
                verify(model.links.get(1), never()).cancel();
            } finally {
                model.close();
            }
        }
    }

    private record ScopeEvent(String value) implements AppEvent { }

    private static final class Probe extends AbstractViewModel {
        final List<SessionSubscriptions> scopes = new ArrayList<>();
        final List<RecordingSubscriber> subscribers = new ArrayList<>();
        final List<Flow.Subscription> links = new ArrayList<>();
        final List<String> notifications = new ArrayList<>();
        Consumer<SessionSubscriptions> registering;
        int logouts;

        @Override protected void onSubscribeEvents(EventSubscribers source, SessionSubscriptions subscriptions) {
            var index = scopes.size();
            scopes.add(subscriptions);
            var subscriber = new RecordingSubscriber();
            subscribers.add(subscriber);
            var link = mock(Flow.Subscription.class);
            links.add(link);
            subscriptions.own(subscriber).onSubscribe(link);
            subscriptions.subscribe(ScopeEvent.class, event -> notifications.add(index + ":" + event.value()));
            if (registering != null) registering.accept(subscriptions);
        }

        @Override protected void onLoggedOut() { logouts++; }
    }

    private static final class RecordingSubscriber extends AbstractSubscriber<String> {
        final List<String> items = new ArrayList<>();
        @Override protected void handle(String item) { items.add(item); }
        @Override public void onError(Throwable error) { throw new AssertionError(error); }
        @Override public void onComplete() { }
    }
}
