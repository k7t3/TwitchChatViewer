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

package com.github.k7t3.tcv.view.group.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

class ChannelGroupCreateMenuItem extends MenuItem {

    private final ChannelGroupRepository repository;

    private final ObservableList<TwitchChannelViewModel> channels;

    ChannelGroupCreateMenuItem(
            ChannelGroupRepository repository,
            ObservableList<TwitchChannelViewModel> channels
    ) {
        super(Resources.getString("group.create"));
        this.repository = repository;
        this.channels = channels;
        setGraphic(new FontIcon(Feather.PLUS));

        addEventHandler(ActionEvent.ACTION, this::action);
    }

    private Window getWindowRecursively(Window window) {
        if (window == null) {
            return null;
        }
        if (window instanceof ContextMenu cm) {
            if (cm.getOwnerWindow() != null) {
                return getWindowRecursively(cm.getOwnerWindow());
            }
            if (cm.getOwnerNode() != null) {
                var scene = cm.getOwnerNode().getScene();
                return scene.getWindow();
            }
        }
        return window;
    }

    private void action(ActionEvent e) {
        e.consume();

        Window owner = getWindowRecursively(getParentPopup());

        var dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle(Resources.getString("group.dialog.create.title"));
        dialog.setHeaderText(Resources.getString("group.dialog.create.header"));
        dialog.setContentText(Resources.getString("group.dialog.create.content"));

        var result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        var name = result.get();
        var group = new ChannelGroup();
        group.setName(name);
        group.getChannels().addAll(channels);
        repository.saveAsync(group);
    }
}
