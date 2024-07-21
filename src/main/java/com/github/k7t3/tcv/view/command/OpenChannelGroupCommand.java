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
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.view.group.ChannelGroupListView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;

public class OpenChannelGroupCommand extends BasicCommand {

    private final ModalPane modalPane;
    private final ChannelGroupRepository groupRepository;

    public OpenChannelGroupCommand(
            ModalPane modalPane,
            ChannelGroupRepository groupRepository,
            ObservableValue<Boolean> condition
    ) {
        this.modalPane = modalPane;
        this.groupRepository = groupRepository;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(new ChannelGroupListViewModel(groupRepository))
                .load();
        var view = tuple.getView();
        var behind = tuple.getCodeBehind();

        modalPane.setPersistent(false);
        modalPane.usePredefinedTransitionFactories(Side.LEFT);
        modalPane.show(view);

        // ListViewの表示要素を先頭にする処理
        behind.onOpened();
    }
}
