package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.prefs.ChatMessageFilterViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.core.cell.TextListCell;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatMessageFilterView implements PreferencesTabView<ChatMessageFilterViewModel> {

    @FXML
    private Label headerLabel;

    @FXML
    private ListView<String> filters;

    @FXML
    private Button plusButton;

    @FXML
    private Button minusButton;

    @InjectViewModel
    private ChatMessageFilterViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        headerLabel.getStyleClass().addAll(Styles.TITLE_4);

        filters.setCellFactory(param -> new TextListCell());
        filters.setEditable(true);
        filters.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);
        filters.setItems(viewModel.getFilters());
        filters.setOnEditCommit(e -> {
            if (e.getNewValue() == null || e.getNewValue().trim().isEmpty()) {
                e.getSource().getItems().remove(e.getIndex());
            } else {
                e.getSource().getItems().set(e.getIndex(), e.getNewValue());
            }
        });

        plusButton.getStyleClass().addAll(Styles.FLAT);
        plusButton.setOnAction(e -> addItem());

        minusButton.getStyleClass().addAll(Styles.FLAT);
        minusButton.setOnAction(e -> removeItem());
    }

    private void addItem() {
        filters.getItems().add("");
    }

    private void removeItem() {
        var index = filters.getSelectionModel().getSelectedIndex();
        if (index < 0) return;

        filters.getItems().remove(index);
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
