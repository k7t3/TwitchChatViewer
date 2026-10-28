package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.event.ShutdownEvent;
import com.github.k7t3.tcv.app.event.UserFilteringEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.utils.notifications.NotificationCenter;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatSessionSubscriptionsTest {
    @ParameterizedTest(name = "aborted chat subscription at {0}")
    @ValueSource(strings = {"logout", "close", "register-before", "register-after", "notification-logout", "notification-close"})
    void abortedRegistrationCannotRetainSessionNotificationsOrAffectTheNextSession(String boundary) {
        try (var fixture = new Fixture()) {
            var failure = new IllegalStateException("notification registration failed");
            if (boundary.startsWith("register")) {
                fixture.failure = failure;
                fixture.failAfterInstall = boundary.equals("register-after");
            } else if (boundary.startsWith("notification")) {
                fixture.firstNotificationRegistration = boundary.endsWith("logout") ? fixture.model::onLogout : fixture.model::close;
            } else {
                fixture.firstFlowRegistration = boundary.equals("logout") ? fixture.model::onLogout : fixture.model::close;
            }

            if (boundary.startsWith("register")) {
                assertThatThrownBy(() -> fixture.model.subscribeEvents(fixture.events)).isSameAs(failure);
            } else {
                assertThatCode(() -> fixture.model.subscribeEvents(fixture.events)).doesNotThrowAnyException();
            }

            assertThat(fixture.sessionRegistrations()).isEmpty();
            assertThat(fixture.flows).hasSize(8);
            fixture.flows.forEach(subscription -> verify(subscription).cancel());
            if (boundary.endsWith("close")) {
                assertThat(fixture.registrations).isEmpty();
                return;
            }
            fixture.assertOnlyLifecycleRemains();
            fixture.model.subscribeEvents(fixture.events);
            assertThat(fixture.sessionRegistrations()).extracting(Registration::type).containsExactly(
                    ChatOpeningEvent.class.getName(), KeywordFilteringEvent.class.getName(), UserFilteringEvent.class.getName());
            assertThat(fixture.flows).hasSize(16);
            fixture.flows.subList(8, 16).forEach(subscription -> verify(subscription, never()).cancel());

            fixture.model.onLogout();

            fixture.assertOnlyLifecycleRemains();
            fixture.flows.forEach(subscription -> verify(subscription).cancel());
        }
    }

    private record Registration(String type, NotificationObserver observer) { }

    private static final class Fixture implements AutoCloseable {
        final List<Registration> registrations = new ArrayList<>();
        final List<Flow.Subscription> flows = new ArrayList<>();
        final NotificationCenter center = mock(NotificationCenter.class);
        final MockedStatic<MvvmFX> mvvm = mockStatic(MvvmFX.class);
        final EventSubscribers events = mock(EventSubscribers.class);
        final ChatRoomContainerViewModel model;
        Runnable firstFlowRegistration;
        Runnable firstNotificationRegistration;
        IllegalStateException failure;
        boolean failAfterInstall;

        Fixture() {
            mvvm.when(MvvmFX::getNotificationCenter).thenReturn(center);
            doAnswer(invocation -> {
                String type = invocation.getArgument(0);
                var registration = new Registration(type, invocation.getArgument(1));
                if (type.equals(ChatOpeningEvent.class.getName()) && firstNotificationRegistration != null) {
                    var callback = firstNotificationRegistration;
                    firstNotificationRegistration = null;
                    callback.run();
                }
                var current = type.equals(KeywordFilteringEvent.class.getName()) ? failure : null;
                if (current != null) failure = null;
                if (current == null || failAfterInstall) registrations.add(registration);
                if (current != null) throw current;
                return null;
            }).when(center).subscribe(anyString(), any(NotificationObserver.class));
            doAnswer(invocation -> {
                registrations.removeIf(registration -> registration.observer() == invocation.getArgument(0));
                return null;
            }).when(center).unsubscribe(any(NotificationObserver.class));
            model = new ChatRoomContainerViewModel(mock(ChatFilters.class),
                    () -> { throw new AssertionError("unexpected resource load"); }, Runnable::run);
            Answer<Void> subscribe = invocation -> {
                Flow.Subscriber<?> subscriber = invocation.getArgument(0);
                var subscription = mock(Flow.Subscription.class);
                flows.add(subscription);
                subscriber.onSubscribe(subscription);
                if (flows.size() == 1 && firstFlowRegistration != null) firstFlowRegistration.run();
                return null;
            };
            doAnswer(subscribe).when(events).subscribeChatEvent(any());
            doAnswer(subscribe).when(events).subscribeMessageEvent(any());
        }

        List<Registration> sessionRegistrations() {
            return registrations.stream().filter(registration -> !List.of(LoginEvent.class.getName(),
                    LogoutEvent.class.getName(), ShutdownEvent.class.getName()).contains(registration.type())).toList();
        }

        void assertOnlyLifecycleRemains() {
            assertThat(registrations).extracting(Registration::type).containsExactly(
                    LoginEvent.class.getName(), LogoutEvent.class.getName(), ShutdownEvent.class.getName());
        }

        @Override public void close() {
            try { model.close(); }
            finally { mvvm.close(); }
        }
    }
}
