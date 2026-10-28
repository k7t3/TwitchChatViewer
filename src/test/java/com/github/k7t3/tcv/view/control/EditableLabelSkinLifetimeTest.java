package com.github.k7t3.tcv.view.control;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(ApplicationExtension.class)
class EditableLabelSkinLifetimeTest {
    private EditableLabel control;
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        control = new EditableLabel("before");
        control.setId("editable");
        stage.setScene(new Scene(new StackPane(control), 360, 160));
        stage.show();
    }

    @BeforeEach
    void focusInputWindow(FxRobot robot) throws Exception {
        robot.interact(() -> {
            stage.toFront();
            stage.requestFocus();
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
            var focused = new AtomicBoolean();
            robot.interact(() -> focused.set(stage.isFocused()));
            return focused.get();
        });
    }

    @ParameterizedTest(name = "discard old {0} handler")
    @ValueSource(strings = {"start", "cancel"})
    void replacingTheSkinDiscardsTheOldStartAndCancelHandlers(String operation, FxRobot robot) {
        var label = new AtomicReference<Label>();
        var pressed = new AtomicReference<MouseEvent>();
        robot.interact(() -> {
            label.set((Label) control.lookup(".label"));
            label.get().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() == 2) pressed.set(event);
            });
        });
        robot.doubleClickOn("#editable .label");
        robot.interact(() -> {
            assertThat(control.isEditing()).isTrue();
            assertThat(pressed.get()).isNotNull();
            var field = (TextField) control.lookup(".text-field");
            var starting = label.get().getOnMousePressed();
            field.setText("discarded");
            control.setSkin(null);
            control.setSkin(new EditableLabelSkin(control));

            assertThatCode(() -> {
                if (operation.equals("start")) starting.handle(pressed.get());
                else field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
            }).doesNotThrowAnyException();
            assertThat(control.getText()).isEqualTo("before");
            assertThat(control.isEditing()).isFalse();
        });
    }

    @ParameterizedTest(name = "finish editing with {0}")
    @ValueSource(strings = {"commit", "cancel"})
    void theCurrentSkinStillCompletesNormalEditing(String operation, FxRobot robot) {
        var committed = new ArrayList<String>();
        var cancelled = new ArrayList<String>();
        robot.interact(() -> {
            control.setOnEditCommit(event -> committed.add(event.getNewValue()));
            control.setOnEditCancel(event -> cancelled.add(event.getNewValue()));
        });
        robot.doubleClickOn("#editable .label");
        robot.write("edited");
        robot.press(operation.equals("commit") ? KeyCode.ENTER : KeyCode.ESCAPE)
                .release(operation.equals("commit") ? KeyCode.ENTER : KeyCode.ESCAPE);
        robot.interact(() -> {
            assertThat(control.isEditing()).isFalse();
            if (operation.equals("commit")) {
                assertThat(control.getText()).isEqualTo("edited");
                assertThat(committed).containsExactly("edited");
                assertThat(cancelled).isEmpty();
            } else {
                assertThat(control.getText()).isEqualTo("before");
                assertThat(committed).isEmpty();
                assertThat(cancelled).containsExactly("before");
            }
            assertThat(((Label) control.lookup(".label")).getText()).isEqualTo(control.getText());
        });
    }

    @Test
    void replacingTheSkinDisconnectsTheOldLabelsText(FxRobot robot) {
        robot.interact(() -> {
            var previous = (Label) control.lookup(".label");
            assertThat(previous.getText()).isEqualTo("before");

            control.setSkin(null);
            control.setSkin(new EditableLabelSkin(control));
            control.setText("after");

            assertThat(previous.getText()).isEqualTo("before");
            assertThat(((Label) control.lookup(".label")).getText()).isEqualTo("after");
        });
    }

    @Test
    void replacingTheSkinDiscardsTheOldEditorsCommit(FxRobot robot) {
        var commits = new AtomicInteger();
        robot.interact(() -> control.setOnEditCommit(event -> commits.incrementAndGet()));
        robot.doubleClickOn("#editable .label");
        robot.interact(() -> {
            assertThat(control.isEditing()).isTrue();
            var field = (TextField) control.lookup(".text-field");
            assertThat(field).isNotNull();
            field.setText("discarded");
            var action = field.getOnAction();

            control.setSkin(null);
            control.setSkin(new EditableLabelSkin(control));

            assertThatCode(() -> action.handle(new ActionEvent())).doesNotThrowAnyException();
            assertThat(control.getText()).isEqualTo("before");
            assertThat(commits).hasValue(0);
            assertThat(control.isEditing()).isFalse();
        });
    }
}
