package com.github.k7t3.tcv.view.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.prefs.KeyBindingPreferencesViewModel;
import com.github.k7t3.tcv.app.prefs.KeywordFilterViewModel;
import com.github.k7t3.tcv.app.prefs.UserFilterViewModel;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.view.keyboard.KeyCombinationCell;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class SettingsTablePageLifetimeTest {
    @Test
    @SuppressWarnings("unchecked")
    void closingUserFiltersDisconnectsTheTableAndItsDeleteButtons(FxRobot robot) {
        robot.interact(() -> {
            var filters = new ChatFilters(mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            filters.getUserEntries().add(new UserFilterEntry("42", "Name", "Comment"));
            var model = new UserFilterViewModel(filters); var item = model.getUsers().getFirst();
            var page = new UserFilterView(); var stage = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(UserFilterView.class).codeBehind(page).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load();
                stage.setScene(new Scene(tuple.getView(), 700, 400)); stage.show();
                tuple.getView().applyCss(); tuple.getView().layout();
                var table = (TableView<UserFilterViewModel.Wrapper>) tuple.getView().lookup("#userTables");
                var trash = table.lookupAll(".button").stream().map(Button.class::cast)
                        .filter(value -> value.getStyleClass().contains("danger")).findFirst().orElseThrow();

                page.close(); page.close();

                assertThat(table.getItems()).isNull();
                assertThat(table.getColumns()).allSatisfy(column -> {
                    assertThat(column.getCellFactory()).isNull();
                    assertThat(column.getCellValueFactory()).isNull();
                });
                trash.fire();
                assertThat(model.getUsers()).containsExactly(item);
                assertThat(item.isRemoved()).isFalse();
                assertThat(trash.getOnAction()).isNull();
            } finally { page.close(); stage.close(); filters.close(); }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void closingKeyBindingsEndsTheCellsAndRetainedResetActions(FxRobot robot) {
        robot.interact(() -> {
            var model = new KeyBindingPreferencesViewModel(new KeyBindingCombinations());
            var item = model.getCombinationList().getFirst();
            var original = KeyCombination.keyCombination("Shortcut+Alt+9"); item.setCombination(original);
            var page = new KeyBindingPreferencesView();
            var stage = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(KeyBindingPreferencesView.class).codeBehind(page)
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load();
                stage.setScene(new Scene(tuple.getView(), 700, 400)); stage.show();
                tuple.getView().applyCss(); tuple.getView().layout();
                var table = (TableView<KeyBindingCombination>) tuple.getView().lookup("#combinationsTableView");
                var column = (TableColumn<KeyBindingCombination, KeyCombination>) table.getColumns().get(1);
                table.getSelectionModel().select(0); table.edit(0, column);
                var cell = table.lookupAll(".key-combination-cell").stream().map(KeyCombinationCell.class::cast)
                        .filter(value -> value.getIndex() == 0).findFirst().orElseThrow();
                assertThat(cell.isEditing()).isTrue();
                var editor = (TextField) cell.getGraphic();
                assertThat(editor.textProperty().isBound()).isTrue();
                var reset = table.getContextMenu().getItems().getFirst().getOnAction();

                page.close(); page.close();

                assertThat(table.getItems()).isNull();
                assertThat(column.getCellFactory()).isNull();
                assertThat(column.getCellValueFactory()).isNull();
                assertThat(cell.getGraphic()).isNull(); assertThat(cell.getText()).isNull();
                assertThat(editor.textProperty().isBound()).isFalse();
                assertThat(editor.getOnKeyPressed()).isNull();
                reset.handle(new ActionEvent());
                cell.commitEdit(KeyCombination.keyCombination("Shortcut+Alt+8"));
                assertThat(item.getCombination()).isEqualTo(original);
                assertThat(model.getCombinationList()).isNotEmpty();
            } finally { page.close(); stage.close(); }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void closingKeywordFiltersEndsValidationAndRetainedAddAndDeleteActions(FxRobot robot) {
        robot.interact(() -> {
            var filters = new ChatFilters(mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            var model = new KeywordFilterViewModel(filters); model.addEntry(KeywordFilterEntry.containsMatch(""));
            var item = model.getKeywordEntries().getFirst();
            var page = new KeywordFilterView(); var stage = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(KeywordFilterView.class).codeBehind(page)
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load();
                stage.setScene(new Scene(tuple.getView(), 700, 400)); stage.show();
                tuple.getView().applyCss(); tuple.getView().layout();
                var table = (TableView<KeywordFilterViewModel.Wrapper>) tuple.getView().lookup("#keywordTableView");
                var cell = table.lookupAll(".keyword-filter-cell").stream().map(TableCell.class::cast)
                        .filter(value -> value.getIndex() == 0).findFirst().orElseThrow();
                var invalid = javafx.css.PseudoClass.getPseudoClass("invalid");
                assertThat(cell.getPseudoClassStates()).contains(invalid);
                var add = ((Button) tuple.getView().lookup("#addButton")).getOnAction();
                var trash = table.lookupAll(".button").stream().map(Button.class::cast)
                        .filter(value -> value.getStyleClass().contains("danger")).findFirst().orElseThrow();

                page.close(); page.close();

                assertThat(table.getItems()).isNull();
                assertThat(cell.getPseudoClassStates()).doesNotContain(invalid);
                item.setKeyword("valid"); item.setKeyword("");
                assertThat(cell.getPseudoClassStates()).doesNotContain(invalid);
                add.handle(new ActionEvent()); trash.fire();
                assertThat(model.getKeywordEntries()).containsExactly(item);
                assertThat(item.isRemoved()).isFalse();
                assertThat(trash.getOnAction()).isNull();
            } finally { page.close(); stage.close(); filters.close(); }
        });
    }
}
