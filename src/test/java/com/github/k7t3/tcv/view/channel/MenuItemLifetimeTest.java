package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import com.github.k7t3.tcv.view.channel.menu.OpenBrowserMenuItem;
import com.github.k7t3.tcv.view.channel.menu.OpenChatMenuItem;
import de.saxsys.mvvmfx.MvvmFX;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MenuItemLifetimeTest {
    @Test void aClosedChatMenuDoesNotPublishAnotherChatOpening(FxRobot robot) {
        robot.interact(() -> {
            var calls = new AtomicInteger(); TypeSafeNotificationObserver<ChatOpeningEvent> received = event -> calls.incrementAndGet();
            var center = MvvmFX.getNotificationCenter(); center.subscribe(ChatOpeningEvent.class.getName(), received);
            try {
                var item = new OpenChatMenuItem(mock(TwitchChannelViewModel.class));
                item.close(); item.fire(); assertThat(calls).hasValue(0);
            } finally { center.unsubscribe(ChatOpeningEvent.class.getName(), received); }
        });
    }

    @Test void aClosedBrowserMenuDoesNotSubmitAnotherTask(FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class);
            var item = new OpenBrowserMenuItem(channel);
            item.close(); item.fire();
            verify(channel, never()).openChannelPageOnBrowser();
        });
    }
}
