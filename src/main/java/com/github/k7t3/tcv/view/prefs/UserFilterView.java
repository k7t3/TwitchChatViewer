package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.UserFilterViewModel;
import com.github.k7t3.tcv.view.core.DeleteButtonTableColumn;
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
        userNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        userNameColumn.setCellValueFactory(features -> features.getValue().userNameProperty());

        commentColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        commentColumn.setCellValueFactory(features -> features.getValue().commentProperty());

        trashColumn.setCellFactory(DeleteButtonTableColumn.create(w -> w.setRemoved(true)));
        trashColumn.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));

        userTables.setItems(viewModel.getUsers());
        userTables.getStyleClass().addAll(Styles.BORDERED, Tweaks.EDGE_TO_EDGE);
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
