package com.github.k7t3.tcv.vm.core;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageNotificator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageNotificator.class);

    private static final double DEFAULT_MESSAGE_WIDTH = 350;

    private static AnchorPane root = null;

    public static void setRoot(AnchorPane root) {
        MessageNotificator.root = root;
    }

    public static void notify(String message) {
        if (root == null) {
            LOGGER.warn("message root pane is null. message[{}]", message);
            return;
        }

        var msg = new Notification(message, new FontIcon(Material2OutlinedAL.INFO));
        msg.getStyleClass().add(Styles.ELEVATED_2);
        msg.setPrefHeight(Region.USE_PREF_SIZE);
        msg.setPrefWidth(DEFAULT_MESSAGE_WIDTH);
        msg.setOnClose(e -> root.getChildren().remove(msg));
        AnchorPane.setTopAnchor(msg, 10d);
        AnchorPane.setRightAnchor(msg, 10d);
        root.getChildren().add(msg);

        var fadeIn = Animations.fadeIn(msg, Duration.millis(700));
        var pause = new PauseTransition(Duration.seconds(3));
        var fadeOut = Animations.fadeOut(msg, Duration.millis(700));

        fadeOut.setOnFinished(e -> root.getChildren().remove(msg));

        var animations = new SequentialTransition(msg, fadeIn, pause, fadeOut);
        animations.playFromStart();
    }

}