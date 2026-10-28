package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import java.util.ArrayList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class OwnedModalCommandTest {
    @Test void hidingReplacingAndLosingAuthorizationReleaseOnlyTheOwnedContent(FxRobot robot) {
        robot.interact(() -> {
            var pane = new ModalPane(); var condition = new SimpleBooleanProperty(true);
            var window = new Stage(); window.setScene(new Scene(new StackPane(pane), 600, 400)); window.show(); pane.applyCss();
            var released = new ArrayList<Integer>();
            var command = new OwnedModalCommand(pane, condition, Side.LEFT) {
                int opening;
                @Override protected Parent load(ModalSession session) {
                    int value = ++opening; session.own(() -> released.add(value)); return new Pane();
                }
            };
            try {
                command.execute(); pane.hide(); assertThat(released).containsExactly(1);
                command.execute(); var replacement = new Pane(); pane.show(replacement);
                assertThat(released).containsExactly(1, 2);
                command.close(); assertThat(pane.getContent()).isSameAs(replacement);
                assertThat(command.isExecutable()).isFalse();
                assertThatThrownBy(command::execute).isInstanceOf(IllegalStateException.class);
                var second = new OwnedModalCommand(pane, condition, Side.LEFT) {
                    @Override protected Parent load(ModalSession session) { session.own(() -> released.add(3)); return new Pane(); }
                };
                try { second.execute(); condition.set(false); assertThat(released).containsExactly(1, 2, 3); }
                finally { second.close(); }
            } finally { command.close(); window.close(); }
        });
    }

    @Test void partialLoadFailuresReleaseAllResourcesAndRetainTheOriginalCause(FxRobot robot) {
        robot.interact(() -> {
            var pane = new ModalPane(); var condition = new SimpleBooleanProperty(true);
            var failure = new IllegalStateException("FXML failed"); var cleanup = new IllegalStateException("controller failed to close");
            var order = new ArrayList<String>();
            var command = new OwnedModalCommand(pane, condition, Side.TOP) {
                @Override protected Parent load(ModalSession session) {
                    session.own(() -> order.add("model"));
                    session.own(() -> { order.add("controller"); throw cleanup; });
                    throw failure;
                }
            };
            try {
                assertThatThrownBy(command::execute).isSameAs(failure)
                        .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanup));
                assertThat(order).containsExactly("controller", "model");
            } finally { command.close(); }
        });
    }

    @Test void reentrantCloseDuringLoadAlsoReleasesResourcesAcquiredAfterClose(FxRobot robot) {
        robot.interact(() -> {
            var pane = new ModalPane(); var condition = new SimpleBooleanProperty(true); var order = new ArrayList<String>();
            var command = new OwnedModalCommand(pane, condition, Side.BOTTOM) {
                @Override protected Parent load(ModalSession session) {
                    session.own(() -> order.add("first")); close();
                    session.own(() -> order.add("late")); return new Pane();
                }
            };
            command.execute();
            assertThat(order).containsExactly("first", "late"); assertThat(pane.isDisplay()).isFalse();
        });
    }
}
