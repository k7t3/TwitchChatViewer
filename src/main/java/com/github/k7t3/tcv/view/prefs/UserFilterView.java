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
import com.github.k7t3.tcv.app.prefs.UserFilterViewModel;
import com.github.k7t3.tcv.view.core.DeleteButtonTableColumn;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.StackedFontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class UserFilterView implements PreferencesPage<UserFilterViewModel> {
    private final ViewResources owned = new ViewResources();

    @FXML
    private TableView<UserFilterViewModel.Wrapper> userTables;

    @FXML
    private TableColumn<UserFilterViewModel.Wrapper, String> userNameColumn;

    @FXML
    private TableColumn<UserFilterViewModel.Wrapper, String> commentColumn;

    @FXML
    private TableColumn<UserFilterViewModel.Wrapper, UserFilterViewModel.Wrapper> trashColumn;

    @InjectViewModel
    private UserFilterViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (owned.isClosed()) { releaseControls(); return; }
        userNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        userNameColumn.setCellValueFactory(features -> features.getValue().userNameProperty());

        commentColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        commentColumn.setCellValueFactory(features -> features.getValue().commentProperty());

        trashColumn.setCellFactory(column -> {
            var cell = new DeleteButtonTableColumn<UserFilterViewModel.Wrapper>(w -> w.setRemoved(true));
            owned.add(cell::close); return cell;
        });
        trashColumn.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));

        userTables.setItems(viewModel.getUsers());
        userTables.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);
    }

    @Override public void close() {
        if (owned.isClosed()) return;
        CloseActions.runAll(owned::close, this::releaseControls);
    }

    private void releaseControls() {
        if (userTables != null) {
            userTables.edit(-1, null);
            userTables.getSelectionModel().clearSelection();
            userTables.setItems(null);
            userTables.setEditable(false);
        }
        if (userNameColumn != null) { userNameColumn.setCellFactory(null); userNameColumn.setCellValueFactory(null); }
        if (commentColumn != null) { commentColumn.setCellFactory(null); commentColumn.setCellValueFactory(null); }
        if (trashColumn != null) { trashColumn.setCellFactory(null); trashColumn.setCellValueFactory(null); }
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.filtering.user");
    }

    @Override
    public Node getGraphic() {
        var stack = new StackedFontIcon();
        stack.setIconCodes(Feather.USER, Feather.SLASH);
        return stack;
    }

}
