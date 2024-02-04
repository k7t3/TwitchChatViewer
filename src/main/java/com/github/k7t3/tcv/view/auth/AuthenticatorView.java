package com.github.k7t3.tcv.view.auth;

import com.github.k7t3.tcv.vm.auth.AuthenticatorViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressBar;

import java.net.URL;
import java.util.ResourceBundle;

public class AuthenticatorView implements FxmlView<AuthenticatorViewModel>, Initializable {

    @FXML
    private Hyperlink authUriLink;

    @FXML
    private Button clipAuthUriButton;

    @FXML
    private ProgressBar progressBar;

    @InjectViewModel
    private AuthenticatorViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        authUriLink.disableProperty().bind(viewModel.initializedProperty().not());
        authUriLink.textProperty().bind(viewModel.authUriProperty());
        authUriLink.setOnAction(e -> viewModel.openAuthUri());

        clipAuthUriButton.visibleProperty().bind(viewModel.initializedProperty());
        clipAuthUriButton.setOnAction(e -> viewModel.clipAuthUri());

        progressBar.progressProperty().bind(viewModel.authorizedProperty().map(d -> d ? 1.0 : -1));
    }

}
