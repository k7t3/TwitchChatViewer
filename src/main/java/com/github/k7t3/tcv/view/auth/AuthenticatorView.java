package com.github.k7t3.tcv.view.auth;

import atlantafx.base.controls.CustomTextField;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

public class AuthenticatorView implements FxmlView<AuthenticatorViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private Hyperlink authUriLink;

    @FXML
    private ImageView qrcodeImageView;

    @FXML
    private Label userCodeLabel;

    @FXML
    private CustomTextField userCodeField;

    @FXML
    private Button openLinkButton;

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

        userCodeField.textProperty().bind(viewModel.userCodeProperty());
        userCodeField.visibleProperty().bind(viewModel.initializedProperty());
        userCodeField.focusedProperty().addListener((ob, o, n) -> {
            if (n) {
                Platform.runLater(() -> userCodeField.selectAll());
            }
        });
        userCodeLabel.visibleProperty().bind(viewModel.initializedProperty());

        openLinkButton.visibleProperty().bind(viewModel.initializedProperty());
        openLinkButton.setOnAction(e -> viewModel.openAuthUri());

        clipAuthUriButton.visibleProperty().bind(viewModel.initializedProperty());
        clipAuthUriButton.setOnAction(e -> viewModel.clipAuthUri());

        progressBar.progressProperty().bind(viewModel.authorizedProperty().map(d -> d ? 1.0 : -1));

        qrcodeImageView.imageProperty().bind(viewModel.qrcodeProperty());

//        root.parentProperty().addListener((ob, o, n) -> {
//            if (n == null) return;
//            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.5));
//        });
    }

}
