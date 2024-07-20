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

package com.github.k7t3.tcv.view.auth;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.layout.InputGroup;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.event.LoginEvent;
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
    private InputGroup userCodeGroup;

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

        userCodeGroup.visibleProperty().bind(viewModel.initializedProperty());
        userCodeField.textProperty().bind(viewModel.userCodeProperty());
        userCodeField.focusedProperty().addListener((ob, o, n) -> {
            if (n) {
                Platform.runLater(() -> userCodeField.selectAll());
            }
        });

        openLinkButton.visibleProperty().bind(viewModel.initializedProperty());
        openLinkButton.setOnAction(e -> viewModel.openAuthUri());

        clipAuthUriButton.visibleProperty().bind(viewModel.initializedProperty());
        clipAuthUriButton.setOnAction(e -> viewModel.clipAuthUri());

        qrcodeImageView.imageProperty().bind(viewModel.qrcodeProperty());

        viewModel.subscribe(LoginEvent.class, e -> progressBar.setProgress(1d));
    }

}
