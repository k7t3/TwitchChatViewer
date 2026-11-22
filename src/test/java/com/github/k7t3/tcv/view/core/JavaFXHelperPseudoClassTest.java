package com.github.k7t3.tcv.view.core;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class JavaFXHelperPseudoClassTest {
    @Test
    void registrationAppliesTheCurrentStateBeforeTheFirstChange(FxRobot robot) {
        robot.interact(() -> {
            var node = new Pane();
            var active = new SimpleBooleanProperty(true);

            var subscription = JavaFXHelper.registerPseudoClass(node, "invalid", active);
            try {
                assertThat(node.getPseudoClassStates()).contains(PseudoClass.getPseudoClass("invalid"));
            } finally { subscription.unsubscribe(); }
        });
    }

    @Test
    void unsubscribingStopsLaterStyleChanges(FxRobot robot) {
        robot.interact(() -> {
            var node = new Pane(); var active = new SimpleBooleanProperty(true);
            var invalid = PseudoClass.getPseudoClass("invalid");
            var subscription = JavaFXHelper.registerPseudoClass(node, "invalid", active);
            try {
                active.set(false);
                assertThat(node.getPseudoClassStates()).doesNotContain(invalid);
                subscription.unsubscribe(); subscription.unsubscribe();
                active.set(true);
                assertThat(node.getPseudoClassStates()).doesNotContain(invalid);
            } finally { subscription.unsubscribe(); }
        });
    }
}
