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

public class KeyBindingPreferencesView implements PreferencesPage<KeyBindingPreferencesViewModel> {

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
        // キーバインド名を扱う列
        bindingTableColumn.setCellValueFactory(p -> p.getValue().bindingProperty());
        bindingTableColumn.setCellFactory(TextFieldTableCell.forTableColumn(new ToStringConverter<>(KeyBinding::getDisplayText)));

        // キーコンビネーションを扱う列
        combinationTableColumn.setCellValueFactory(param -> param.getValue().combinationProperty());
        combinationTableColumn.setCellFactory(cell -> new KeyCombinationCell());

        // アイテムを追加
        combinationsTableView.setItems(viewModel.getCombinationList());

        combinationsTableView.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);

        // 削除ボタンのデザインを割り当て
        resetAllButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.DANGER);
    }

    @FXML
    private void resetAll(ActionEvent e) {
        var alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(root.getScene().getWindow());
        alert.setTitle(Resources.getString("alert.title.confirmation"));
        alert.setHeaderText(Resources.getString("prefs.keybind.reset.confirm.header"));
        alert.setContentText(Resources.getString("prefs.keybind.reset.confirm.content"));
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        var result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            viewModel.resetAll();
        }
    }

    @FXML
    private void resetBinding(ActionEvent e) {
        var item = combinationsTableView.getSelectionModel().getSelectedItem();
        if (item != null) {
            item.resetKeyCombination();
        }
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
