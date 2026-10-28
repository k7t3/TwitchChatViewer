package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
@ExtendWith(ApplicationExtension.class)
class ViewModelEventSourceTest {
    @Test
    void loginWaitsForAnExplicitEventSourceWithoutInitializingAnotherApplication(FxRobot robot) {
        var model = new Probe();
        var source = mock(EventSubscribers.class);
        try {
            robot.interact(() -> {
                try (var helper = mockConstruction(AppHelper.class)) {

                    assertThatCode(() -> model.publish(new LoginEvent())).doesNotThrowAnyException();

                    assertThat(model.starts).isZero();
                    assertThat(model.source).isNull();
                    model.setEventSubscribers(source);
                    model.publish(new LoginEvent());
                    model.publish(new LoginEvent());
                    assertThat(model.source).isSameAs(source);
                    assertThat(model.starts).isEqualTo(1);
                    assertThat(helper.constructed()).isEmpty();
                }
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void delegatedLifecycleIsDrivenOnlyByTheOwner(FxRobot robot) {
        var source = mock(EventSubscribers.class);
        var model = new Probe();
        try {
            robot.interact(() -> {
                model.delegateLifecycleToOwner();
                model.publish(new LoginEvent());
                model.publish(new LogoutEvent());
                assertThat(model.source).isNull();
                assertThat(model.logouts).isZero();

                model.subscribeEvents(source);
                model.onLogout();
                model.publish(new LogoutEvent());

                assertThat(model.source).isSameAs(source);
                assertThat(model.logouts).isEqualTo(1);
            });
        } finally {
            robot.interact(model::close);
        }
    }
    @Test
    void loginUsesTheRuntimeEventSourceProvidedByItsOwner(FxRobot robot) {
        var source = mock(EventSubscribers.class);
        var model = new Probe();
        try {
            robot.interact(() -> {
                model.setEventSubscribers(source);
                model.publish(new LoginEvent());
                assertThat(model.source).isSameAs(source);
            });
        } finally {
            robot.interact(model::close);
        }
    }

    private static final class Probe extends AbstractViewModel {
        EventSubscribers source;
        int logouts;
        int starts;
        @Override protected void onSubscribeEvents(EventSubscribers source, SessionSubscriptions subscriptions) { this.source = source; starts++; }
        @Override protected void onLoggedOut() { logouts++; }
    }
}
