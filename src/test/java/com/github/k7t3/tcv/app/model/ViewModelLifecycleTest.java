package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(ApplicationExtension.class)
class ViewModelLifecycleTest {

    @ParameterizedTest(name = "notification in progress after unsubscribe, typed={0}")
    @ValueSource(booleans = {false, true})
    void unsubscribeDiscardsRetiredObserversDuringAnOngoingPublication(boolean typed, FxRobot robot) {
        var model = new RecordingGroupRepository();
        var received = new ArrayList<String>();
        TypeSafeNotificationObserver<SampleEvent> observer = event -> received.add(event.value());
        try {
            robot.interact(() -> {
                model.subscribe(SampleEvent.class, event -> {
                    if (!event.value().equals("ongoing publication")) return;
                    if (typed) model.unsubscribe(SampleEvent.class, observer);
                    else model.unsubscribe(observer);
                    model.subscribe(SampleEvent.class, observer);
                });
                model.subscribe(SampleEvent.class, observer);
                model.publish(new SampleEvent("ongoing publication"));
                assertThat(model.isClosed()).isFalse();
                assertThat(received).isEmpty();
                model.publish(new SampleEvent("new subscription"));
                assertThat(received).containsExactly("new subscription");
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void repeatedLoginSubscribesOnlyOncePerSession(FxRobot robot) {
        var model = new RecordingGroupRepository();
        try {
            robot.interact(() -> {
                model.publish(new LoginEvent());
                model.publish(new LoginEvent());
            });
            robot.interact(() -> assertThat(model.loginCount).isEqualTo(1));

            robot.interact(() -> {
                model.publish(new LogoutEvent());
                model.publish(new LoginEvent());
            });
            robot.interact(() -> assertThat(model.loginCount).isEqualTo(2));
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closeReleasesScreenSpecificNotificationSubscriptions(FxRobot robot) {
        var model = new RecordingGroupRepository();
        var received = new ArrayList<String>();
        TypeSafeNotificationObserver<SampleEvent> observer = event -> received.add(event.value());
        try {
            robot.interact(() -> {
                model.subscribe(SampleEvent.class, observer);
                model.publish(new SampleEvent("before close"));
                model.close();
                model.publish(new SampleEvent("after close"));
            });

            robot.interact(() -> assertThat(received).containsExactly("before close"));
        } finally {
            robot.interact(() -> {
                model.unsubscribe(observer);
                model.removeLifecycleSubscriptions();
            });
        }
    }

    private record SampleEvent(String value) implements AppEvent {
    }

    @Test
    void closedViewModelDoesNotSubscribeToNextLogin(FxRobot robot) {
        var model = new RecordingGroupRepository();
        try {
            robot.interact(() -> {
                model.close();
                model.publish(new LoginEvent());
            });

            robot.interact(() -> assertThat(model.loginCount).isZero());
        } finally {
            robot.interact(model::removeLifecycleSubscriptions);
        }
    }

    private static final class RecordingGroupRepository extends ChannelGroupRepository {
        private int loginCount;

        private RecordingGroupRepository() {
            super(mock(ChannelGroupEntityService.class), Runnable::run, ignored -> {},
                    unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            setEventSubscribers(mock(EventSubscribers.class));
        }

        @Override
        protected void onSubscribeEvents(EventSubscribers eventSubscribers, SessionSubscriptions subscriptions) {
            loginCount++;
        }

        private void removeLifecycleSubscriptions() {
            unsubscribe(onLogin);
            unsubscribe(onLogout);
            unsubscribe(onShutdown);
        }
    }
}
