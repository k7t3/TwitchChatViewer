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

package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.command.LoadCredentialCommand;
import com.github.k7t3.tcv.view.core.OwnedDialog;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.function.Supplier;

/** 認証メニューが、ログイン画面とログアウト確認を所有する。Main のモデルは借用する。 */
public class AuthenticationMenuItem extends MenuItem implements AutoCloseable {
    private final LoadCredentialCommand loginCommand;
    private final OwnedDialog<ButtonType> confirmations;
    private final Pane rootPane;
    private final MainViewModel viewModel;
    private final BooleanProperty authorized = new SimpleBooleanProperty();
    private final ChangeListener<Boolean> authorizationChanged;
    private final TypeSafeNotificationObserver<LoginEvent> loginChanged;
    private boolean closed;

    public AuthenticationMenuItem(ModalPane pane, CredentialStore store, Pane root, MainViewModel model) {
        this(pane, store, root, model, () -> new Alert(Alert.AlertType.CONFIRMATION));
    }

    AuthenticationMenuItem(ModalPane pane, CredentialStore store, Pane root, MainViewModel model, Supplier<Alert> dialogs) {
        rootPane = root; viewModel = model;
        confirmations = new OwnedDialog<>(dialogs);
        loginCommand = new LoadCredentialCommand(pane, authorized.not(), () -> model.createAuthenticator(store));
        authorizationChanged = (observable, previous, current) -> { if (!closed) confirmations.cancel(); };
        loginChanged = event -> { if (!closed) confirmations.cancel(); };
        try {
            getStyleClass().add("auth-menu-item");
            var loginIcon = new FontIcon(Feather.LOG_IN); var logoutIcon = new FontIcon(Feather.LOG_OUT);
            graphicProperty().bind(authorized.map(value -> value ? logoutIcon : loginIcon));
            var loginText = Resources.getString("main.menu.login"); var logoutText = Resources.getString("main.menu.logout");
            textProperty().bind(authorized.map(value -> value ? logoutText : loginText));
            authorized.addListener(authorizationChanged);
            model.subscribe(LoginEvent.class, loginChanged);
            setOnAction(event -> {
                if (closed) return;
                if (isAuthorized()) logout(); else loginCommand.execute();
            });
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> { if (failure instanceof RuntimeException exception) throw exception; throw (Error) failure; }, this::close);
            throw failure;
        }
    }

    private void logout() {
        confirmations.show(dialog -> {
            var scene = rootPane.getScene();
            dialog.initOwner(scene == null ? null : scene.getWindow());
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setTitle(Resources.getString("alert.title.confirmation"));
            dialog.setHeaderText(Resources.getString("logout.confirm.header"));
            dialog.setContentText(Resources.getString("logout.confirm.content"));
        }).filter(result -> result == ButtonType.OK).ifPresent(result -> {
            if (!closed && isAuthorized()) viewModel.logout();
        });
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> viewModel.unsubscribe(LoginEvent.class, loginChanged),
                confirmations::close, loginCommand::close, () -> {
                    authorized.removeListener(authorizationChanged); authorized.unbind();
                    textProperty().unbind(); graphicProperty().unbind(); setGraphic(null); setOnAction(null);
                });
    }

    public BooleanProperty authorizedProperty() { return authorized; }
    public boolean isAuthorized() { return authorized.get(); }
}
