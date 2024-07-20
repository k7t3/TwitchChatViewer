/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.Map;
import java.util.Objects;

public enum Theme {

    PRIMER("Primer", new PrimerLight(), new PrimerDark()),

    CUPERTINO("Cupertino", new CupertinoLight(), new CupertinoDark()),

    NORD("Nord", new NordLight(), new NordDark());

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private final String themeName;

    private final Map<ThemeType, atlantafx.base.theme.Theme> themes;

    Theme(String themeName, atlantafx.base.theme.Theme lighter, atlantafx.base.theme.Theme darker) {
        this.themeName = themeName;
        themes = Map.of(ThemeType.LIGHTER, lighter, ThemeType.DARKER, darker);
    }

    public String getThemeName() {
        return themeName;
    }

    void applyTheme(Scene scene, ThemeType type, boolean withAnimation) {
        // システムテーマは受け入れない
        if (type == ThemeType.SYSTEM) throw new IllegalArgumentException("cannot accept a system theme");

        // フェードアニメーション
        if (withAnimation) {
            animateThemeChange(scene, Duration.millis(750));
        }

        var theme = themes.get(type);
        Application.setUserAgentStylesheet(Objects.requireNonNull(theme.getUserAgentStylesheet()));
        scene.getRoot().pseudoClassStateChanged(DARK, theme.isDarkMode());
    }

    private void animateThemeChange(Scene scene, Duration duration) {
        var snapshot = scene.snapshot(null);
        var root = (Pane) scene.getRoot();

        var imageView = new ImageView(snapshot);
        root.getChildren().add(imageView); // add snapshot on top

        var transition = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(imageView.opacityProperty(), 1, Interpolator.EASE_OUT)),
                new KeyFrame(duration, new KeyValue(imageView.opacityProperty(), 0, Interpolator.EASE_OUT))
        );
        transition.setOnFinished(e -> root.getChildren().remove(imageView));
        transition.play();
    }
}
