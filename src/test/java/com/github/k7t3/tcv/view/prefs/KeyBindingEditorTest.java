package com.github.k7t3.tcv.view.prefs;

import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.prefs.KeyBindingPreferencesViewModel;
import com.github.k7t3.tcv.view.keyboard.KeyCombinationCell;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class KeyBindingEditorTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void enterCommitsAndEscapeCancelsWithoutAcceptingLaterEvents(boolean commit, FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Editor()) {
                var cell = ui.beginEditing(); var field = (TextField) cell.getGraphic();
                var detector = field.getOnKeyPressed();
                Event.fireEvent(field, key(KeyCode.K, true, true));
                var chosen = new KeyCodeCombination(KeyCode.K, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN);
                assertThat(field.getText()).isEqualTo(chosen.getDisplayText());

                Event.fireEvent(field, key(commit ? KeyCode.ENTER : KeyCode.ESCAPE, false, false));

                var expected = commit ? chosen : ui.original;
                assertThat(ui.item.getCombination()).isEqualTo(expected);
                assertThat(cell.isEditing()).isFalse();
                assertThat(cell.getGraphic()).isNull();
                assertThat(cell.getText()).isEqualTo(expected.getDisplayText());
                detector.handle(key(KeyCode.N, true, true));
                Event.fireEvent(field, key(KeyCode.ENTER, false, false));
                cell.commitEdit(KeyCombination.keyCombination("Shortcut+Alt+8"));
                assertThat(ui.item.getCombination()).isEqualTo(expected);
            }
        });
    }

    @Test
    void aReusedRowCannotAcceptThePreviousEditorsKeys(FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Editor()) {
                var cell = ui.beginEditing(); var field = (TextField) cell.getGraphic();
                var next = new KeyBindingCombination(ui.item.getBinding(), ui.original);
                ui.table.getItems().set(0, next);
                ui.table.layout();
                Event.fireEvent(field, key(KeyCode.K, true, true));
                Event.fireEvent(field, key(KeyCode.ENTER, false, false));
                assertThat(ui.item.getCombination()).isEqualTo(ui.original);
                assertThat(next.getCombination()).isEqualTo(ui.original);
                assertThat(cell.isEditing()).isFalse();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resetChangesTheDraftOnlyAfterAnAffirmativeAnswer(boolean yes, FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog(); when(dialog.showAndWait()).thenReturn(Optional.of(yes ? ButtonType.YES : ButtonType.NO));
            try (var ui = new Editor(() -> dialog)) {
                ui.reset.fire();
                assertThat(ui.item.getCombination()).isEqualTo(yes ? ui.item.getBinding().getDefaultCombination() : ui.original);
                verify(dialog, times(1)).close(); verify(dialog).setResult(null);
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void endingThePageDuringConfirmationRejectsTheLateYes(boolean duringFactory, FxRobot robot) {
        robot.interact(() -> {
            var ref = new AtomicReference<Editor>(); var dialog = dialog();
            when(dialog.showAndWait()).thenAnswer(call -> { ref.get().page.close(); return Optional.of(ButtonType.YES); });
            try (var ui = new Editor(() -> {
                if (duringFactory) ref.get().page.close();
                return dialog;
            })) {
                ref.set(ui);
                var action = ui.reset.getOnAction();
                ui.reset.fire(); action.handle(new ActionEvent());
                assertThat(ui.item.getCombination()).isEqualTo(ui.original);
                verify(dialog, times(duringFactory ? 0 : 1)).showAndWait();
                verify(dialog, times(1)).close();
            }
        });
    }

    private static KeyEvent key(KeyCode code, boolean shift, boolean alt) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, alt, false);
    }

    @SuppressWarnings("unchecked")
    private static Dialog<ButtonType> dialog() {
        var dialog = (Dialog<ButtonType>) mock(Dialog.class);
        when(dialog.getDialogPane()).thenReturn(new DialogPane()); return dialog;
    }

    static final class Editor implements AutoCloseable {
        final KeyBindingPreferencesView page;
        final Stage stage = new Stage();
        final TableView<KeyBindingCombination> table;
        final TableColumn<KeyBindingCombination, KeyCombination> column;
        final KeyBindingCombination item;
        final KeyCombination original = KeyCombination.keyCombination("Shortcut+Alt+9");
        final Button reset;
        Editor() { this(() -> { throw new AssertionError("unexpected reset dialog"); }); }
        @SuppressWarnings("unchecked")
        Editor(Supplier<? extends Dialog<ButtonType>> dialogs) {
            page = new KeyBindingPreferencesView(dialogs);
            var model = new KeyBindingPreferencesViewModel(new KeyBindingCombinations());
            item = model.getCombinationList().getFirst(); item.setCombination(original);
            var tuple = FluentViewLoader.fxmlView(KeyBindingPreferencesView.class).codeBehind(page).viewModel(model)
                    .resourceBundle(Resources.getResourceBundle()).load();
            stage.setScene(new Scene(tuple.getView(), 700, 400)); stage.show();
            tuple.getView().applyCss(); tuple.getView().layout();
            table = (TableView<KeyBindingCombination>) tuple.getView().lookup("#combinationsTableView");
            column = (TableColumn<KeyBindingCombination, KeyCombination>) table.getColumns().get(1);
            reset = (Button) tuple.getView().lookup("#resetAllButton");
        }
        KeyCombinationCell beginEditing() {
            table.edit(0, column);
            return table.lookupAll(".key-combination-cell").stream().map(KeyCombinationCell.class::cast)
                    .filter(cell -> cell.getIndex() == 0).findFirst().orElseThrow();
        }
        @Override public void close() { page.close(); stage.close(); }
    }
}
