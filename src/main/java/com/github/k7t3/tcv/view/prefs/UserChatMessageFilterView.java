package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.prefs.UserChatMessageFilterViewModel;
import com.github.k7t3.tcv.view.core.ButtonTableColumnCell;
import com.github.k7t3.tcv.app.core.Resources;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class UserChatMessageFilterView implements PreferencesTabView<UserChatMessageFilterViewModel> {

    @FXML
    private TableView<UserChatMessageFilterViewModel.HideUserViewModel> users;

    @FXML
    private TableColumn<UserChatMessageFilterViewModel.HideUserViewModel, String> userNameColumn;

    @FXML
    private TableColumn<UserChatMessageFilterViewModel.HideUserViewModel, String> commentColumn;

    @FXML
    private TableColumn<UserChatMessageFilterViewModel.HideUserViewModel, UserChatMessageFilterViewModel.HideUserViewModel> trashColumn;

    @InjectViewModel
    private UserChatMessageFilterViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // セルの定義
        userNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        commentColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        trashColumn.setCellFactory(column -> new DeleteButtonCell());

        userNameColumn.setCellValueFactory(features -> features.getValue().userNameProperty());
        commentColumn.setCellValueFactory(features -> features.getValue().commentProperty());
        trashColumn.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));

        users.setItems(viewModel.getUsers());
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.filter.user");
    }

    @Override
    public Node getGraphic() {
        var stack = new StackedFontIcon();
        stack.setIconCodes(Feather.USER, Feather.SLASH);
        return stack;
    }

    private static class DeleteButtonCell extends ButtonTableColumnCell<UserChatMessageFilterViewModel.HideUserViewModel> {

        @Override
        protected Button createButton() {
            var button = new Button(null, new FontIcon(Feather.TRASH));
            button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.DANGER);
            button.setOnAction(e -> {
                var item = getItem();
                item.remove();
            });
            return button;
        }

    }

}
