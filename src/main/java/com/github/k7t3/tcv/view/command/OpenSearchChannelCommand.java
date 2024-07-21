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
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;

public class OpenSearchChannelCommand extends BasicCommand {

    private final ModalPane modalPane;
    private final ChannelViewModelRepository repository;

    public OpenSearchChannelCommand(
            ModalPane modalPane,
            ChannelViewModelRepository repository,
            ObservableValue<Boolean> condition
    ) {
        this.modalPane = modalPane;
        this.repository = repository;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        var helper = AppHelper.getInstance();
        var viewModel = new SearchChannelViewModel(repository);
        viewModel.twitchProperty().bind(helper.twitchProperty());

        var tuple = FluentViewLoader
                .fxmlView(SearchChannelView.class)
                .viewModel(viewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        var view = tuple.getView();

        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(false);
        modalPane.show(view);

        // フォーカスを遅延させないとIMEが正常に動作しなくなる
        Platform.runLater(() -> tuple.getCodeBehind().getKeywordField().requestFocus());
    }
}
