package com.github.k7t3.tcv.view.channel;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public class LiveInfoView extends BorderPane {

    private static final String STYLE_CLASS = "live-info-view";
    private static final String TITLE_CLASS = "live-info-title";
    private static final String LABEL_CONTAINER = "label-container";

    private final Label uptimeLabel;

    private final ObjectProperty<TwitchChannelViewModel> channel = new SimpleObjectProperty<>();

    public LiveInfoView(TwitchChannelViewModel channel) {
        this();
        setChannel(channel);
    }

    public LiveInfoView() {
        var userNameLabel = new Label();
        var gameNameLabel = new Label();
        var streamTitleLabel = new Label();
        var viewerCountLabel = new Label();
        uptimeLabel = new Label();

        userNameLabel.getStyleClass().addAll(Styles.TEXT_CAPTION, TITLE_CLASS);
        BorderPane.setAlignment(userNameLabel, Pos.CENTER);
        userNameLabel.textProperty().bind(channelProperty().map(TwitchChannelViewModel::getUserName));

        gameNameLabel.textProperty().bind(channelProperty().map(TwitchChannelViewModel::getGameName));

        streamTitleLabel.setWrapText(true);
        streamTitleLabel.getStyleClass().addAll(Styles.TEXT_SMALL);
        streamTitleLabel.textProperty().bind(channelProperty().map(TwitchChannelViewModel::getStreamTitle));

        viewerCountLabel.getStyleClass().addAll(Styles.DANGER, Styles.TEXT_SMALL);
        viewerCountLabel.textProperty().bind(channelProperty().map(c -> Integer.toString(c.getStreamInfo().viewerCount())));

        uptimeLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        viewerCountLabel.setGraphic(new FontIcon(Feather.USER));
        uptimeLabel.setGraphic(new FontIcon(Feather.CLOCK));


        var vbox = new VBox(
                gameNameLabel,
                streamTitleLabel,
                viewerCountLabel,
                uptimeLabel
        );
        vbox.getStyleClass().addAll(LABEL_CONTAINER);

        getStyleClass().add(STYLE_CLASS);
        setTop(userNameLabel);
        setCenter(vbox);
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("LiveInfoView.css")).toExternalForm());
    }

    public void computeUptimeLabel() {
        var channel = getChannel();
        var now = LocalDateTime.now();
        var startedAt = channel.getStreamInfo().startedAt();
        var between = Duration.between(startedAt, now);
        var minutes = between.toMinutes();

        if (minutes < 60) {
            uptimeLabel.setText("%d m".formatted(minutes));
        } else {
            var hours = minutes / 60;
            minutes = minutes - hours * 60;
            uptimeLabel.setText("%d h %d m".formatted(hours, minutes));
        }
    }

    public ObjectProperty<TwitchChannelViewModel> channelProperty() { return channel; }
    public TwitchChannelViewModel getChannel() { return channel.get(); }
    public void setChannel(TwitchChannelViewModel channel) { this.channel.set(channel); }

}
