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

import java.util.function.Consumer;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Parent;
import java.util.function.Supplier;

public class OpenSearchChannelCommand extends OwnedModalCommand {
    private final Consumer<SearchChannelViewModel> configure;
    private final Supplier<SearchChannelViewModel> modelFactory;
    public OpenSearchChannelCommand(ModalPane pane, ObservableValue<Boolean> condition,
                                    Supplier<SearchChannelViewModel> models, Consumer<SearchChannelViewModel> configure) {
        super(pane, condition, Side.TOP);
        this.modelFactory = java.util.Objects.requireNonNull(models);
        this.configure = java.util.Objects.requireNonNull(configure);
    }
    @Override protected Parent load(ModalSession session) {
        var model = modelFactory.get(); session.own(model::close);
        var controller = new SearchChannelView(); session.own(controller::close);
        configure.accept(model);
        var tuple = FluentViewLoader.fxmlView(SearchChannelView.class).codeBehind(controller).viewModel(model)
                .resourceBundle(Resources.getResourceBundle()).load();
        // IME のため、モーダルの接続後に共通の所有者が遅延実行する。
        session.onShown(() -> controller.getKeywordField().requestFocus());
        return tuple.getView();
    }
}
