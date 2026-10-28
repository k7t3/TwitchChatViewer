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
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.group.ChannelGroupListView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Parent;

public class OpenChannelGroupCommand extends OwnedModalCommand {
    private final ChannelGroupRepository repository;
    private final AppPreferences preferences;

    public OpenChannelGroupCommand(ModalPane pane, ChannelGroupRepository repository, AppPreferences preferences,
                                   ObservableValue<Boolean> condition) {
        super(pane, condition, Side.LEFT);
        this.repository = repository; this.preferences = preferences;
    }

    @Override protected Parent load(ModalSession session) {
        var model = new ChannelGroupListViewModel(repository); session.own(model::close);
        model.delegateLifecycleToOwner();
        var controller = new ChannelGroupListView(preferences); session.own(controller::close);
        var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class).codeBehind(controller).viewModel(model)
                .resourceBundle(Resources.getResourceBundle()).load();
        session.onShown(controller::onOpened);
        return tuple.getView();
    }
}
