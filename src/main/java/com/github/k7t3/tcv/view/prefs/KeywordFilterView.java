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
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterType;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.KeywordFilterViewModel;
import com.github.k7t3.tcv.view.core.DeleteButtonTableColumn;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class KeywordFilterView implements PreferencesPage<KeywordFilterViewModel> {
    private final ViewResources owned = new ViewResources();

    @FXML
    private TableView<KeywordFilterViewModel.Wrapper> keywordTableView;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, KeywordFilterType> filterTypeColumn;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, String> keywordColumn;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, KeywordFilterViewModel.Wrapper> trashColumn;

    @FXML
    private Button addButton;

    @FXML
    private MenuItem addMenuItem;

    @InjectViewModel
    private KeywordFilterViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (owned.isClosed()) { releaseControls(); return; }
        keywordTableView.setEditable(true);
        keywordTableView.setItems(viewModel.getKeywordEntries());
        keywordTableView.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);

        var nameMap = new HashMap<KeywordFilterType, String>();
        nameMap.put(KeywordFilterType.CONTAINS, Resources.getString("chat.filter.type.contains"));
        nameMap.put(KeywordFilterType.PREFIX_MATCH, Resources.getString("chat.filter.type.prefix"));
        nameMap.put(KeywordFilterType.EXACT_MATCH, Resources.getString("chat.filter.type.exact"));
        nameMap.put(KeywordFilterType.REGEXP, Resources.getString("chat.filter.type.regexp"));

        filterTypeColumn.setCellValueFactory(features -> features.getValue().filterTypeProperty());
        filterTypeColumn.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(KeywordFilterType keywordFilterType) {
                return nameMap.get(keywordFilterType);
            }

            @Override
            public KeywordFilterType fromString(String s) {
                return nameMap.entrySet()
                        .stream()
                        .filter(e -> e.getValue().equals(s))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElseThrow();
            }
        }, FXCollections.observableArrayList(KeywordFilterType.values())));
        filterTypeColumn.setEditable(true);

        keywordColumn.setCellValueFactory(features -> features.getValue().keywordProperty());
        keywordColumn.setCellFactory(column -> {
            var cell = new KeywordFilterCell(); owned.add(cell::close); return cell;
        });
        keywordColumn.setEditable(true);

        trashColumn.setCellFactory(column -> {
            var cell = new DeleteButtonTableColumn<KeywordFilterViewModel.Wrapper>(entry -> entry.setRemoved(true));
            owned.add(cell::close); return cell;
        });
        trashColumn.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));

        addButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.ACCENT);
        addButton.setOnAction(e -> {
            addItem();
            e.consume();
        });
    }

    @FXML
    private void addItem() {
        if (owned.isClosed()) return;
        var entry = KeywordFilterEntry.containsMatch("");
        viewModel.addEntry(entry);
    }

    @Override public void close() {
        if (owned.isClosed()) return;
        CloseActions.runAll(owned::close, this::releaseControls);
    }

    private void releaseControls() {
        if (addButton != null) addButton.setOnAction(null);
        if (addMenuItem != null) addMenuItem.setOnAction(null);
        if (keywordTableView != null) {
            keywordTableView.edit(-1, null);
            keywordTableView.getSelectionModel().clearSelection();
            if (keywordTableView.getContextMenu() != null) keywordTableView.getContextMenu().hide();
            keywordTableView.setItems(null);
            keywordTableView.setEditable(false);
        }
        if (keywordColumn != null) { keywordColumn.setCellFactory(null); keywordColumn.setCellValueFactory(null); }
        if (filterTypeColumn != null) { filterTypeColumn.setCellFactory(null); filterTypeColumn.setCellValueFactory(null); }
        if (trashColumn != null) { trashColumn.setCellFactory(null); trashColumn.setCellValueFactory(null); }
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.filtering.keyword");
    }

    @Override
    public Node getGraphic() {
        return new FontIcon(Feather.EYE_OFF);
    }

}
