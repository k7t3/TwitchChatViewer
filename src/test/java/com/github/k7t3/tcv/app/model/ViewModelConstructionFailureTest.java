package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.event.LogoutEvent;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.utils.notifications.NotificationCenter;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ViewModelConstructionFailureTest {
    @ParameterizedTest(name = "lifecycle constructor registration failure, installed={0}")
    @ValueSource(booleans = {false, true})
    void failedConstructionReleasesEarlierNotificationsWithoutCallingSubclassCleanup(boolean installed) {
        var center = mock(NotificationCenter.class);
        var registrations = new ArrayList<NotificationObserver>();
        var failure = new IllegalStateException("lifecycle registration failed");
        var subclassCleanup = new AtomicBoolean();
        doAnswer(invocation -> {
            boolean fail = invocation.getArgument(0).equals(LogoutEvent.class.getName());
            if (!fail || installed) registrations.add(invocation.getArgument(1));
            if (fail) throw failure;
            return null;
        }).when(center).subscribe(anyString(), any(NotificationObserver.class));
        doAnswer(invocation -> {
            registrations.remove(invocation.<NotificationObserver>getArgument(0));
            return null;
        }).when(center).unsubscribe(any(NotificationObserver.class));
        try (var mvvm = mockStatic(MvvmFX.class)) {
            mvvm.when(MvvmFX::getNotificationCenter).thenReturn(center);

            assertThatThrownBy(() -> new AbstractViewModel() {
                @Override protected void onClose() { subclassCleanup.set(true); }
            }).isSameAs(failure);

            assertThat(registrations).isEmpty();
            assertThat(subclassCleanup.get()).isFalse();
        }
    }
}
