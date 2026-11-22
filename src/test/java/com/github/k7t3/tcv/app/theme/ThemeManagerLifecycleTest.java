package com.github.k7t3.tcv.app.theme;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.collections.SetChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ThemeManagerLifecycleTest {
    @Test
    void changingThemeDuringSystemStylingKeepsTheSelectedFamilyOnOsChanges(FxRobot robot) {
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            var colors = new SimpleObjectProperty<ColorScheme>(ColorScheme.DARK);
            var manager = new ThemeManager(colors);
            var root = new Pane();
            var dark = PseudoClass.getPseudoClass("dark");
            root.getPseudoClassStates().addListener((SetChangeListener<PseudoClass>) change -> {
                if (change.wasAdded() && change.getElementAdded().equals(dark)) manager.setTheme(Theme.NORD);
            });
            try {
                manager.setScene(new Scene(root));
                manager.setTheme(Theme.PRIMER);

                manager.setThemeType(ThemeType.SYSTEM);
                colors.set(ColorScheme.LIGHT);

                assertThat(manager.getTheme()).isEqualTo(Theme.NORD);
                assertThat(Application.getUserAgentStylesheet()).contains("nord-light.css");
                assertThat(root.getPseudoClassStates()).doesNotContain(dark);
            } finally {
                manager.close();
                Application.setUserAgentStylesheet(previous);
            }
        });
    }

    @Test
    void selectingLightDuringSystemStylingDoesNotResumeFollowingOsChanges(FxRobot robot) {
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            var colors = new SimpleObjectProperty<ColorScheme>(ColorScheme.DARK);
            var manager = new ThemeManager(colors);
            var root = new Pane();
            var dark = PseudoClass.getPseudoClass("dark");
            root.getPseudoClassStates().addListener((SetChangeListener<PseudoClass>) change -> {
                if (change.wasAdded() && change.getElementAdded().equals(dark)) {
                    manager.setThemeType(ThemeType.LIGHTER);
                }
            });
            try {
                manager.setScene(new Scene(root));
                manager.setTheme(Theme.PRIMER);

                manager.setThemeType(ThemeType.SYSTEM);
                colors.set(ColorScheme.LIGHT);
                colors.set(ColorScheme.DARK);

                assertThat(manager.getThemeType()).isEqualTo(ThemeType.LIGHTER);
                assertThat(root.getPseudoClassStates()).doesNotContain(dark);
            } finally {
                manager.close();
                Application.setUserAgentStylesheet(previous);
            }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void closingDuringInitialSystemStylingCannotRegisterALateSystemSubscription(FxRobot robot) {
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            var colors = spy(new SimpleObjectProperty<ColorScheme>(ColorScheme.DARK));
            var manager = new ThemeManager(colors);
            var root = new Pane();
            var dark = PseudoClass.getPseudoClass("dark");
            root.getPseudoClassStates().addListener((SetChangeListener<PseudoClass>) change -> {
                if (change.wasAdded() && change.getElementAdded().equals(dark)) manager.close();
            });
            try {
                manager.setScene(new Scene(root));
                manager.setTheme(Theme.PRIMER);

                manager.setThemeType(ThemeType.SYSTEM);

                assertThat(root.getPseudoClassStates()).contains(dark);
                verify(colors, never()).addListener(any(ChangeListener.class));
            } finally {
                manager.close();
                Application.setUserAgentStylesheet(previous);
            }
        });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void closingRemovesTheSystemSubscriptionAndDoesNotStyleTheOldSceneAgain(FxRobot robot) {
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            var colors = spy(new SimpleObjectProperty<ColorScheme>(ColorScheme.LIGHT));
            var manager = new ThemeManager(colors);
            var root = new Pane();
            try {
                manager.setScene(new Scene(root));
                manager.setTheme(Theme.PRIMER);
                manager.setThemeType(ThemeType.SYSTEM);
                var listener = ArgumentCaptor.forClass(ChangeListener.class);
                verify(colors).addListener(listener.capture());
                manager.close();
                verify(colors).removeListener(listener.getValue());
                colors.set(ColorScheme.DARK);
                assertThat(root.getPseudoClassStates()).doesNotContain(PseudoClass.getPseudoClass("dark"));
            } finally {
                manager.close();
                Application.setUserAgentStylesheet(previous);
            }
        });
    }
}
