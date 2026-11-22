package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelListBrowserTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void browserOpeningReceivesTheSelectedUrlsWithoutBorrowingTheMutableSelection(FxRobot robot) {
        robot.interact(() -> {
            var browsers = mock(BrowserActions.class);
            when(browsers.openAll(any())).thenAnswer(ignored -> FXTask.of(true));
            var model = new TwitchChannelListViewModel(browsers);
            var first = channel("first"); var second = channel("second");
            try {
                model.getSelectedChannels().addAll(first, second);
                model.openSelectedChannelPageOnBrowser();
                model.getSelectedChannels().clear();
                var links = ArgumentCaptor.forClass((Class<List<String>>) (Class) List.class);
                verify(browsers).openAll(links.capture());
                assertThat(links.getValue()).containsExactly("https://www.twitch.tv/first", "https://www.twitch.tv/second");
                verify(first, never()).openChannelPageOnBrowser(); verify(second, never()).openChannelPageOnBrowser();
            } finally { model.close(); }
        });
    }

    @Test
    void logoutClearsTheOldSelectionAndCancelsItsBrowserActions(FxRobot robot) {
        robot.interact(() -> {
            var browsers = mock(BrowserActions.class);
            var model = new TwitchChannelListViewModel(browsers);
            try {
                model.getSelectedChannels().add(channel("first"));
                model.onLogout();
                assertThat(model.getSelectedChannels()).isEmpty();
                verify(browsers).cancelAll();
            } finally { model.close(); }
            verify(browsers).close();
        });
    }

    @Test
    void endedListsRejectNewCommandsAndCannotBeReboundAfterClose(FxRobot robot) {
        robot.interact(() -> {
            var browsers = mock(BrowserActions.class);
            var model = new TwitchChannelListViewModel(browsers);
            model.close();
            model.getSelectedChannels().add(channel("old"));
            assertThatThrownBy(model::openSelectedChannelPageOnBrowser).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(model::joinChat).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> model.bindChannels(javafx.collections.FXCollections.observableArrayList())).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void logoutCannotOpenOldChannelsUntilAnotherSessionIsBound(FxRobot robot) {
        robot.interact(() -> {
            var browsers = mock(BrowserActions.class);
            var model = new TwitchChannelListViewModel(browsers);
            try {
                model.onLogout(); model.getSelectedChannels().add(channel("old"));
                assertThatThrownBy(model::openSelectedChannelPageOnBrowser).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(model::joinChat).isInstanceOf(IllegalStateException.class);
                model.bindChannels(javafx.collections.FXCollections.observableArrayList());
                model.getSelectedChannels().setAll(channel("next"));
                model.openSelectedChannelPageOnBrowser();
                verify(browsers).openAll(List.of("https://www.twitch.tv/next"));
            } finally { model.close(); }
        });
    }

    private static TwitchChannelViewModel channel(String login) {
        var channel = mock(TwitchChannelViewModel.class);
        when(channel.getBroadcaster()).thenReturn(new Broadcaster(login, login, login, "", ""));
        return channel;
    }
}
