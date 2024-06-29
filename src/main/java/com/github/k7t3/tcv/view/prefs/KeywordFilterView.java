package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterType;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.KeywordFilterViewModel;
import com.github.k7t3.tcv.view.core.DeleteButtonTableColumn;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class KeywordFilterView implements PreferencesTabView<KeywordFilterViewModel> {

    @FXML
    private TableView<KeywordFilterViewModel.Wrapper> keywordTableView;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, KeywordFilterType> filterTypeColumn;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, String> keywordColumn;

    @FXML
    private TableColumn<KeywordFilterViewModel.Wrapper, KeywordFilterViewModel.Wrapper> trashColumn;

    @FXML
    private MenuItem addMenuItem;

    @FXML
    private MenuItem removeMenuItem;

    @InjectViewModel
    private KeywordFilterViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
        keywordColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        keywordColumn.setEditable(true);

        trashColumn.setCellFactory(DeleteButtonTableColumn.create(entry -> entry.setRemoved(true)));
        trashColumn.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
    }

    @FXML
    private void addItem() {
        var entry = KeywordFilterEntry.containsMatch("");
        viewModel.addEntry(entry);
    }

    @FXML
    private void removeItem() {
        var items = keywordTableView.getSelectionModel().getSelectedItems();
        items.forEach(item -> item.setRemoved(true));
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.filter");
    }

    @Override
    public Node getGraphic() {
        return new FontIcon(Feather.EYE_OFF);
    }

}
