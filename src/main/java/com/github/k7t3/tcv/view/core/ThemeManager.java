package com.github.k7t3.tcv.view.core;

import atlantafx.base.theme.*;
import com.github.k7t3.tcv.app.core.OS;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// AtlantaFX samplerアプリケーションを参照
public class ThemeManager {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    public static final Theme DEFAULT_THEME;

    static {
        if (OS.isMac()) {
            DEFAULT_THEME = new CupertinoLight();
        } else {
            DEFAULT_THEME = new PrimerLight();
        }
    }

    private Scene scene;

    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>();

    private ThemeManager() {
        theme.addListener((ob, o, n) -> themeChanged(o, n));
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    private List<Theme> themes;

    public List<Theme> getThemes() {
        if (themes == null) {
            themes = List.of(
                    new NordLight(),
                    new NordDark(),
                    new PrimerLight(),
                    new PrimerDark(),
                    new CupertinoLight(),
                    new CupertinoDark(),
                    new Dracula()
            );
        }
        return themes;
    }

    public Optional<Theme> findTheme(String name) {
        return getThemes().stream()
                .filter(t -> Objects.equals(name, t.getName()))
                .findFirst();
    }

    private void themeChanged(Theme older, Theme theme) {
        if (older != null) {
            animateThemeChange(Duration.millis(750));
        }

        Application.setUserAgentStylesheet(Objects.requireNonNull(theme.getUserAgentStylesheet()));
        scene.getRoot().pseudoClassStateChanged(DARK, theme.isDarkMode());
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

    public ObjectProperty<Theme> themeProperty() { return theme; }
    public Theme getTheme() { return theme.get(); }
    public void setTheme(Theme theme) { this.theme.set(theme); }

    public static ThemeManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ThemeManager INSTANCE = new ThemeManager();
    }

}
