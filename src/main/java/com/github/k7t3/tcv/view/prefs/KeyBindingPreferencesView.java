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

package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.app.prefs.KeyBindingPreferencesViewModel;
import com.github.k7t3.tcv.view.core.ToStringConverter;
import com.github.k7t3.tcv.view.core.OwnedDialog;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.keyboard.KeyCombinationCell;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Supplier;

public class KeyBindingPreferencesView implements PreferencesPage<KeyBindingPreferencesViewModel> {
    private final ViewResources owned = new ViewResources();
    private final OwnedDialog<ButtonType> confirmation;

    public KeyBindingPreferencesView() { this(() -> new Alert(Alert.AlertType.WARNING)); }
    KeyBindingPreferencesView(Supplier<? extends Dialog<ButtonType>> dialogs) { confirmation = new OwnedDialog<>(dialogs); }

    @FXML
    private Pane root;

    @FXML
    private TableView<KeyBindingCombination> combinationsTableView;

    @FXML
    private TableColumn<KeyBindingCombination, KeyBinding> bindingTableColumn;

    @FXML
    private TableColumn<KeyBindingCombination, KeyCombination> combinationTableColumn;

    @FXML
    private Button resetAllButton;

    @FXML
    private MenuItem resetMenuItem;

    @InjectViewModel
    private KeyBindingPreferencesViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (owned.isClosed()) { releaseControls(); return; }
        // キーバインド名を扱う列
        bindingTableColumn.setCellValueFactory(p -> p.getValue().bindingProperty());
        bindingTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new ToStringConverter<>(KeyBinding::getDisplayText)));

        // キーコンビネーションを扱う列
        combinationTableColumn.setCellValueFactory(param -> param.getValue().combinationProperty());
        combinationTableColumn.setCellFactory(column -> {
            var cell = new KeyCombinationCell(); owned.add(cell::close); return cell;
        });

        // アイテムを追加
        combinationsTableView.setItems(viewModel.getCombinationList());

        combinationsTableView.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);

        // 削除ボタンのデザインを割り当て
        resetAllButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.DANGER);
    }

    @FXML
    private void resetAll(ActionEvent e) {
        if (owned.isClosed()) return;
        confirmation.show(alert -> {
            alert.initOwner(root.getScene().getWindow());
            alert.setTitle(Resources.getString("alert.title.confirmation"));
            alert.setHeaderText(Resources.getString("prefs.keybind.reset.confirm.header"));
            alert.setContentText(Resources.getString("prefs.keybind.reset.confirm.content"));
            alert.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        }).filter(result -> result == ButtonType.YES).ifPresent(result -> {
            if (!owned.isClosed()) viewModel.resetAll();
        });
    }

    @FXML
    private void resetBinding(ActionEvent e) {
        if (owned.isClosed()) return;
        var item = combinationsTableView.getSelectionModel().getSelectedItem();
        if (item != null) {
            item.resetKeyCombination();
        }
    }

    @Override public void close() {
        if (owned.isClosed()) return;
        CloseActions.runAll(owned::close, confirmation::close, this::releaseControls);
    }

    private void releaseControls() {
        if (resetAllButton != null) resetAllButton.setOnAction(null);
        if (resetMenuItem != null) resetMenuItem.setOnAction(null);
        if (combinationsTableView != null) {
            combinationsTableView.edit(-1, null);
            combinationsTableView.getSelectionModel().clearSelection();
            if (combinationsTableView.getContextMenu() != null) combinationsTableView.getContextMenu().hide();
            combinationsTableView.setItems(null);
            combinationsTableView.setEditable(false);
        }
        if (combinationTableColumn != null) { combinationTableColumn.setCellFactory(null); combinationTableColumn.setCellValueFactory(null); }
        if (bindingTableColumn != null) { bindingTableColumn.setCellFactory(null); bindingTableColumn.setCellValueFactory(null); }
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.keybind");
    }

    @Override
    public Node getGraphic() {
        return new FontIcon(FontAwesomeSolid.KEYBOARD);
    }
}
