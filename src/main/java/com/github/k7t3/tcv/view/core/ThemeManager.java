package com.github.k7t3.tcv.view.core;

import atlantafx.base.theme.Theme;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.Objects;

// AtlantaFX samplerアプリケーションを参照
public class ThemeManager {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private Scene scene;

    private Theme currentTheme;

    private ThemeManager() {
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public void setTheme(Theme theme) {

        if (currentTheme != null) {
            animateThemeChange(Duration.millis(750));
        }

        Application.setUserAgentStylesheet(Objects.requireNonNull(theme.getUserAgentStylesheet()));
        scene.getRoot().pseudoClassStateChanged(DARK, theme.isDarkMode());

        currentTheme = theme;
    }

    private void animateThemeChange(Duration duration) {
        Image snapshot = scene.snapshot(null);
        Pane root = (Pane) scene.getRoot();

        ImageView imageView = new ImageView(snapshot);
        root.getChildren().add(imageView); // add snapshot on top

        var transition = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(imageView.opacityProperty(), 1, Interpolator.EASE_OUT)),
                new KeyFrame(duration, new KeyValue(imageView.opacityProperty(), 0, Interpolator.EASE_OUT))
        );
        transition.setOnFinished(e -> root.getChildren().remove(imageView));
        transition.play();
    }

    public static ThemeManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ThemeManager INSTANCE = new ThemeManager();
    }

}
