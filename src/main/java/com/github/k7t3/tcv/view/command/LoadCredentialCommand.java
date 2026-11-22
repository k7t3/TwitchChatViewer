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

package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.core.ViewResources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Parent;
import java.util.function.Supplier;

/** 初期認証のモデル・FXML・開始予約を、同じ一時画面の所有へまとめる。 */
public class LoadCredentialCommand extends OwnedModalCommand {
    private final Supplier<AuthenticatorViewModel> authenticatorFactory;
    private boolean opening;

    public LoadCredentialCommand(ModalPane pane, ObservableValue<Boolean> condition,
                                 Supplier<AuthenticatorViewModel> authenticatorFactory) {
        super(pane, condition, Side.TOP);
        this.authenticatorFactory = authenticatorFactory;
    }

    @Override protected boolean mayOpen() { return !opening && !isRunning(); }
    @Override protected boolean persistent() { return true; }

    @Override protected Parent load(ModalSession session) {
        opening = true;
        session.own(() -> opening = false);
        var model = authenticatorFactory.get();
        var modelLifetime = new ViewResources();
        modelLifetime.add(model::close);
        session.own(modelLifetime::close);
        if (session.isReleased()) return null;
        var controller = new AuthenticatorView(modelLifetime::close);
        session.own(controller::close);
        session.own(() -> { running.unbind(); running.set(false); });
        running.bind(model.busyProperty());
        var root = FluentViewLoader.fxmlView(AuthenticatorView.class).codeBehind(controller)
                .viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
        session.onShown(model::beginAuthentication);
        return root;
    }
}
