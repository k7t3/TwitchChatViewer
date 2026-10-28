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
import javafx.collections.ObservableMap;
import javafx.collections.MapChangeListener;
import javafx.collections.FXCollections;
import javafx.scene.control.TextInputDialog;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.ViewResources;
import java.util.Comparator;

/** メニューに表示する項目とその監視を所有する。モデル・選択一覧は借用する。 */
public class ChannelGroupMenu extends Menu implements AutoCloseable {
    private final ChannelGroupRepository repository;
    private final ObservableList<TwitchChannelViewModel> channels;
    private final ChannelGroupCreateMenuItem createMenuItem;
    private final SeparatorMenuItem createSeparator = new SeparatorMenuItem();
    private ViewResources groupItems = new ViewResources();
    private boolean closed;
    private Runnable releaseProjection = () -> {};

    public ChannelGroupMenu(ChannelGroupRepository repository,
                            ObservableMap<TwitchChannelViewModel, ?> rooms) {
        this(repository, FXCollections.observableArrayList(rooms.keySet()));
        MapChangeListener<TwitchChannelViewModel, Object> changed = change -> {
            if (!closed) channels.setAll(List.copyOf(rooms.keySet()));
        };
        rooms.addListener(changed);
        releaseProjection = () -> { rooms.removeListener(changed); channels.clear(); };
    }

    public ChannelGroupMenu(ChannelGroupRepository repository, ObservableList<TwitchChannelViewModel> channels) {
        this(repository, channels, TextInputDialog::new);
    }

    ChannelGroupMenu(ChannelGroupRepository repository, ObservableList<TwitchChannelViewModel> channels,
                     Supplier<TextInputDialog> dialogs) {
        super(Resources.getString("group.channels"));
        this.repository = repository;
        this.channels = channels;
        createMenuItem = new ChannelGroupCreateMenuItem(repository, channels, dialogs);
        // 空の Menu は開けないため、初回の表示前にも作成操作を用意する。
        getItems().setAll(createMenuItem, createSeparator);
        setOnShowing(event -> refreshItems());
    }

    public void refreshItems() {
        if (closed) return;
        var previous = groupItems;
        groupItems = new ViewResources();
        getItems().clear();
        previous.close();
        if (closed) return;
        var groups = repository.getAll().stream().sorted(Comparator.comparing(ChannelGroup::getName)).toList();
        if (closed) return;
        getItems().setAll(createMenuItem, createSeparator);
        for (var group : groups) {
            var item = new ChannelGroupMenuItem(repository, channels, group);
            groupItems.add(item::close);
            if (closed) return;
            getItems().add(item);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(disableProperty()::unbind, () -> setOnShowing(null), this::hide, createMenuItem::close, groupItems::close,
                releaseProjection, () -> getItems().clear());
    }
}
