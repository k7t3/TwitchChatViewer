package com.github.k7t3.tcv.app.theme;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(ApplicationExtension.class)
class JapaneseTypographyTest {

    @Test
    void secondaryScenesInheritHiraginoWhenInstalled(FxRobot robot) {
        assumeTrue(Font.getFamilies().contains("Hiragino Sans"));
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            try (var manager = new ThemeManager()) {
                manager.setScene(new Scene(new StackPane()));
                manager.setTheme(Theme.CUPERTINO);
                manager.setThemeType(ThemeType.LIGHTER);

                var label = new Label("の？〜―（）「」。、漢字 ABC 123");
                var secondary = new Scene(new StackPane(label));
                secondary.getRoot().applyCss();

                assertThat(label.getFont()).isEqualTo(Font.font("Hiragino Sans", label.getFont().getSize()));
            } finally {
                Application.setUserAgentStylesheet(previous);
            }
        });
    }

    @Test
    void boldLabelsUseTheBoldHiraginoFace(FxRobot robot) {
        assumeTrue(Font.getFamilies().contains("Hiragino Sans"));
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            try (var manager = new ThemeManager()) {
                var label = new Label("チャンネル一覧");
                label.getStyleClass().add("title-2");
                var scene = new Scene(new StackPane(label));
                manager.setScene(scene);
                manager.setTheme(Theme.PRIMER);
                manager.setThemeType(ThemeType.DARKER);

                scene.getRoot().applyCss();

                assertThat(label.getFont()).isEqualTo(Font.font("Hiragino Sans", FontWeight.BOLD, 24.5));
            } finally {
                Application.setUserAgentStylesheet(previous);
            }
        });
    }

    @Test
    void editableTextInheritsHiraginoWhenInstalled(FxRobot robot) {
        assumeTrue(Font.getFamilies().contains("Hiragino Sans"));
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            try (var manager = new ThemeManager()) {
                var field = new TextField("チャンネルを検索");
                var scene = new Scene(new StackPane(field));
                manager.setScene(scene);
                manager.setTheme(Theme.CUPERTINO);
                manager.setThemeType(ThemeType.LIGHTER);

                scene.getRoot().applyCss();

                assertThat(field.getFont()).isEqualTo(Font.font("Hiragino Sans", 14));
            } finally {
                Application.setUserAgentStylesheet(previous);
            }
        });
    }
}
