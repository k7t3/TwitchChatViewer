package com.github.k7t3.tcv.view.action;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.core.Resources;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Pane;

public class LogoutAction implements Action {

    private final Pane rootPane;

    public LogoutAction(Pane rootPane) {
        this.rootPane = rootPane;
    }

    @Override
    public void run() {
        var parent = rootPane.getScene().getWindow();

        var confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(parent);
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(Resources.getString("logout.confirm.header"));
        confirm.setContentText(Resources.getString("logout.confirm.content"));

        var result = confirm.showAndWait();
        result.ifPresent(b -> {
            if (b == ButtonType.OK) {
                var helper = AppHelper.getInstance();
                helper.logout();
            }
        });
    }

}
