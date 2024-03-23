package com.github.k7t3.tcv.view.chat;

import atlantafx.base.controls.Popover;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.StageBoundsListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.time.LocalDateTime;

public class ChatRoomViewUtils {

    private ChatRoomViewUtils() {
    }

    public static void initializeFloatableStage(FloatableStage stage, ChatRoomViewModel chatRoom) {
        // ウインドウの座標設定を取り出す
        var prefs = AppPreferences.getInstance();

        // チャットにおける識別子を使用して設定を取り出す
        var windowPrefs = prefs.getWindowPreferences(chatRoom.getIdentity());

        // 保存されている座標を割り当て
        var bounds = windowPrefs.getStageBounds();
        bounds.apply(stage);

        // 座標の追跡設定
        var listener = new StageBoundsListener();
        listener.install(stage);

        stage.setOnCloseRequest(e -> {
            // ウインドウを閉じるときに座標を記録
            var current = listener.getCurrent();
            windowPrefs.setStageBounds(current);
        });
    }

    public static void installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
        var gameNameLabel = new Label();
        gameNameLabel.setWrapText(true);

        var streamTitleLabel = new Label();
        streamTitleLabel.setWrapText(true);
        streamTitleLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        var viewerCountLabel = new Label();
        viewerCountLabel.setGraphic(new FontIcon(FontAwesomeSolid.USER));
        viewerCountLabel.getStyleClass().add(Styles.DANGER);

        // アップタイムはポップアップを表示したときに計算する
        var uptimeLabel = new Label();
        uptimeLabel.setGraphic(new FontIcon(FontAwesomeSolid.CLOCK));

        var vbox = new VBox(gameNameLabel, streamTitleLabel, viewerCountLabel, uptimeLabel);
        vbox.setPrefWidth(300);
        vbox.setSpacing(4);
        vbox.setPadding(new Insets(10, 0, 10, 0));

        var pop = new Popover(vbox);
        pop.titleProperty().bind(channel.observableUserName());
        pop.setHeaderAlwaysVisible(true);
        pop.setDetachable(false);
        pop.setArrowLocation(Popover.ArrowLocation.TOP_LEFT);

        pop.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> {
            gameNameLabel.setText(channel.getStreamInfo().gameName());
            streamTitleLabel.setText(channel.getStreamInfo().title());
            viewerCountLabel.setText(Integer.toString(channel.getStreamInfo().viewerCount()));

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
        });

        node.setOnMousePressed(e -> {
            if (channel.isLive() && !pop.isShowing()) {
                pop.show(node);
                e.consume();
            }
        });
        node.setOnMouseEntered(e -> {
            if (channel.isLive()) {
                pop.show(node);
            }
        });
        node.setOnMouseExited(e -> pop.hide());
    }

}
