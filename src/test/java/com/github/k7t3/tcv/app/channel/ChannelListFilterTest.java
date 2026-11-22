package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelListFilterTest {
    enum Match { DISPLAY_NAME, LOGIN, GAME }

    @ParameterizedTest @EnumSource(Match.class)
    void matchingLiveFollowedChannelsRemainVisibleUnderBothRestrictions(Match match, FxRobot robot) {
        robot.interact(() -> {
            var channel = channel(match, true);
            var model = new TwitchChannelListViewModel(mock(BrowserActions.class));
            try {
                model.bindChannels(FXCollections.observableArrayList(channel));
                model.setOnlyLive(true); model.setOnlyFollow(true); model.setFilter("NEEDLE");
                assertThat(model.getLoadedChannels()).containsExactly(channel);
                model.setFilter("absent");
                assertThat(model.getLoadedChannels()).isEmpty();
            } finally { model.close(); }
        });
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = "   ")
    void aBlankKeywordKeepsTheStateRestrictions(String keyword, FxRobot robot) {
        robot.interact(() -> {
            var inactive = channel(Match.LOGIN); var active = channel(Match.LOGIN, true);
            var model = new TwitchChannelListViewModel(mock(BrowserActions.class));
            try {
                model.bindChannels(FXCollections.observableArrayList(inactive, active));
                model.setFilter(keyword);
                assertThat(model.getLoadedChannels()).containsExactlyInAnyOrder(inactive, active);
                model.setOnlyLive(true); model.setOnlyFollow(true);
                assertThat(model.getLoadedChannels()).containsExactly(active);
            } finally { model.close(); }
        });
    }

    @ParameterizedTest
    @EnumSource(Match.class)
    void matchingAsciiLettersDoesNotDependOnTheOperatingSystemLocale(Match match, FxRobot robot) {
        robot.interact(() -> {
            var previous = Locale.getDefault();
            var model = new TwitchChannelListViewModel(mock(BrowserActions.class));
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                var channel = channel(match);
                switch (match) {
                    case DISPLAY_NAME -> when(channel.getUserName()).thenReturn("indigo");
                    case LOGIN -> when(channel.getUserLogin()).thenReturn("indigo");
                    case GAME -> when(channel.getGameName()).thenReturn("indigo");
                }
                model.bindChannels(FXCollections.observableArrayList(channel));

                model.setFilter("INDIGO");

                assertThat(model.getLoadedChannels()).containsExactly(channel);
            } finally { Locale.setDefault(previous); model.close(); }
        });
    }

    @ParameterizedTest
    @EnumSource(Match.class)
    void textMatchesRespectBothRestrictions(Match match, FxRobot robot) {
        robot.interact(() -> {
            var channel = channel(match);
            var model = new TwitchChannelListViewModel(mock(BrowserActions.class));
            try {
                model.bindChannels(FXCollections.observableArrayList(channel));
                model.setFilter(" Needle ");
                assertThat(model.getLoadedChannels()).containsExactly(channel);

                model.setOnlyLive(true);
                assertThat(model.getLoadedChannels()).isEmpty();
                model.setOnlyLive(false);
                model.setOnlyFollow(true);
                assertThat(model.getLoadedChannels()).isEmpty();
                model.setOnlyLive(true);
                assertThat(model.getLoadedChannels()).isEmpty();

                model.setOnlyLive(false);
                model.setOnlyFollow(false);
                assertThat(model.getLoadedChannels()).containsExactly(channel);
            } finally { model.close(); }
        });
    }

    private static TwitchChannelViewModel channel(Match match) {
        return channel(match, false);
    }

    private static TwitchChannelViewModel channel(Match match, boolean active) {
        var channel = mock(TwitchChannelViewModel.class);
        var live = new SimpleBooleanProperty(active);
        var persistent = new SimpleBooleanProperty(true);
        when(channel.liveProperty()).thenReturn(live);
        when(channel.observableViewerCount()).thenReturn(new SimpleIntegerProperty(-1).asObject());
        when(channel.persistentProperty()).thenReturn(persistent);
        when(channel.isPersistent()).thenAnswer(ignored -> persistent.get());
        when(channel.isLive()).thenAnswer(ignored -> live.get());
        when(channel.isFollowing()).thenReturn(active);
        when(channel.getUserName()).thenReturn(match == Match.DISPLAY_NAME ? "needle" : "display");
        when(channel.getUserLogin()).thenReturn(match == Match.LOGIN ? "needle" : "login");
        when(channel.getGameName()).thenReturn(match == Match.GAME ? "needle" : "game");
        return channel;
    }
}
