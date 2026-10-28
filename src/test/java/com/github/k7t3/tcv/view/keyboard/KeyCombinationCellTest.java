package com.github.k7t3.tcv.view.keyboard;

import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class KeyCombinationCellTest {
    @Test
    void anEmptyCellCanCancelEditing(FxRobot robot) {
        robot.interact(() -> {
            var cell = new TestCell();
            assertThatCode(cell::cancelEdit).doesNotThrowAnyException();
            assertThat(cell.getText()).isNull();
            assertThat(cell.getGraphic()).isNull();
        });
    }

    @Test
    void anEmptiedCellClearsDuplicateStyle(FxRobot robot) {
        robot.interact(() -> {
            var combination = new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN);
            var item = new KeyBindingCombination(null, combination); item.setDuplicated(true);
            var row = new Row(); row.show(item);
            var cell = new TestCell(); cell.updateTableRow(row); cell.show(combination, false);
            var duplicated = PseudoClass.getPseudoClass("duplicated");
            assertThat(cell.getPseudoClassStates()).contains(duplicated);

            cell.show(null, true);

            assertThat(cell.getPseudoClassStates()).doesNotContain(duplicated);
            assertThat(cell.getTooltip()).isNull();
            assertThat(cell.getGraphic()).isNull();
            item.setDuplicated(false); item.setDuplicated(true);
            assertThat(cell.getPseudoClassStates()).doesNotContain(duplicated);
        });
    }

    @Test
    void replacingARowWithTheSameCombinationMovesTheDuplicateObservation(FxRobot robot) {
        robot.interact(() -> {
            var combination = new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN);
            var first = new KeyBindingCombination(null, combination); first.setDuplicated(true);
            var next = new KeyBindingCombination(null, combination);
            var row = new Row(); row.show(first);
            var cell = new TestCell(); cell.updateTableRow(row); cell.show(combination, false);
            var duplicated = PseudoClass.getPseudoClass("duplicated");
            assertThat(cell.getPseudoClassStates()).contains(duplicated);

            row.show(next);

            assertThat(cell.getPseudoClassStates()).doesNotContain(duplicated);
            assertThat(cell.getTooltip()).isNull();
            first.setDuplicated(false); first.setDuplicated(true);
            assertThat(cell.getPseudoClassStates()).doesNotContain(duplicated);
            next.setDuplicated(true);
            assertThat(cell.getPseudoClassStates()).contains(duplicated);
            assertThat(cell.getTooltip()).isNotNull();
            cell.show(null, true);
        });
    }

    static class TestCell extends KeyCombinationCell {
        void show(KeyCombination value, boolean empty) { updateItem(value, empty); }
    }

    static class Row extends TableRow<KeyBindingCombination> {
        void show(KeyBindingCombination value) { updateItem(value, value == null); }
    }
}
