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
import com.github.k7t3.tcv.view.core.ViewResources;
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

public class AuthenticatorView implements FxmlView<AuthenticatorViewModel>, Initializable, AutoCloseable {
    private final ViewResources owned = new ViewResources();
    private final Runnable endModel;

    public AuthenticatorView() { this(null); }

    /** 外側の所有者がある場合は、その一回限りの解放を取り外し時にも要求する。 */
    public AuthenticatorView(Runnable endModel) { this.endModel = endModel; }

    private void releaseModel() {
        if (endModel != null) endModel.run();
        else if (viewModel != null) viewModel.close();
    }

    @Override public void close() { owned.close(); }

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

    @FXML
    private Label errorLabel;

    @FXML
    private Button retryButton;

    @InjectViewModel
    private AuthenticatorViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (owned.isClosed()) { releaseModel(); return; }
        owned.add(this::releaseModel);
        owned.add(this::detachControls);
        javafx.beans.value.ChangeListener<javafx.scene.Parent> parentChanged = (observable, oldParent, newParent) -> {
            if (oldParent != null && newParent == null) close();
        };
        root.parentProperty().addListener(parentChanged);
        owned.add(() -> root.parentProperty().removeListener(parentChanged));
        authUriLink.disableProperty().bind(viewModel.initializedProperty().not());
        authUriLink.textProperty().bind(viewModel.authUriProperty());
        authUriLink.setOnAction(e -> { if (!owned.isClosed()) viewModel.openAuthUri(); });

        userCodeGroup.visibleProperty().bind(viewModel.initializedProperty());
        userCodeField.textProperty().bind(viewModel.userCodeProperty());
        javafx.beans.value.ChangeListener<Boolean> focused = (ob, o, n) -> {
            if (n && !owned.isClosed()) {
                Platform.runLater(() -> { if (!owned.isClosed() && userCodeField.isFocused()) userCodeField.selectAll(); });
            }
        };
        userCodeField.focusedProperty().addListener(focused);
        owned.add(() -> userCodeField.focusedProperty().removeListener(focused));

        openLinkButton.visibleProperty().bind(viewModel.initializedProperty());
        openLinkButton.setOnAction(e -> { if (!owned.isClosed()) viewModel.openAuthUri(); });

        clipAuthUriButton.visibleProperty().bind(viewModel.initializedProperty());
        clipAuthUriButton.setOnAction(e -> { if (!owned.isClosed()) viewModel.clipAuthUri(); });

        qrcodeImageView.imageProperty().bind(viewModel.qrcodeProperty());

        progressBar.progressProperty().bind(viewModel.busyProperty().map(busy -> busy ? -1d : 0d));
        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        retryButton.visibleProperty().bind(errorLabel.visibleProperty());
        retryButton.managedProperty().bind(retryButton.visibleProperty());
        retryButton.setOnAction(event -> { if (!owned.isClosed()) viewModel.beginAuthentication(); });
    }

    private void detachControls() {
        if (authUriLink != null) {
            authUriLink.disableProperty().unbind(); authUriLink.textProperty().unbind(); authUriLink.setOnAction(null);
        }
        if (userCodeGroup != null) userCodeGroup.visibleProperty().unbind();
        if (userCodeField != null) userCodeField.textProperty().unbind();
        for (var button : new Button[] {openLinkButton, clipAuthUriButton, retryButton}) {
            if (button != null) { button.visibleProperty().unbind(); button.managedProperty().unbind(); button.setOnAction(null); }
        }
        if (qrcodeImageView != null) { qrcodeImageView.imageProperty().unbind(); qrcodeImageView.setImage(null); }
        if (progressBar != null) { progressBar.progressProperty().unbind(); progressBar.setProgress(0); }
        if (errorLabel != null) {
            errorLabel.textProperty().unbind(); errorLabel.visibleProperty().unbind(); errorLabel.managedProperty().unbind();
        }
    }
}
