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
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.WindowEvent;

import java.util.Comparator;

public class ChannelGroupMenu extends Menu {

    /**
     * チャネルグループのリポジトリ
     */
    private final ChannelGroupRepository repository;

    /**
     * チャンネルグループに登録・削除されるチャンネルリスト
     */
    private final ObservableList<TwitchChannelViewModel> channels;

    public ChannelGroupMenu(
            ChannelGroupRepository repository,
            ObservableList<TwitchChannelViewModel> channels
    ) {
        super(Resources.getString("group.channels"));
        this.repository = repository;
        this.channels = channels;
        init();
    }

    private void init() {
        // メニューを開いたときに各メニューアイテムを生成する
        EventHandler<WindowEvent> showing = e -> refreshItems();

        parentPopupProperty().addListener((ob, o, n) -> {
            if (o != null) o.removeEventHandler(WindowEvent.WINDOW_SHOWING, showing);
            if (n != null) n.addEventHandler(WindowEvent.WINDOW_SHOWING, showing);
        });
    }

    // チャンネルグループを新規作成するメニューとセパレータ
    private ChannelGroupCreateMenuItem createMenuItem;
    private SeparatorMenuItem createSeparator;

    public void refreshItems() {
        var groups = repository.getAll();

        // 登録されているチャンネルグループに対応するメニューアイテムを生成する
        var groupMenuItems = groups.stream()
                .sorted(Comparator.comparing(ChannelGroup::getName))
                .map(g -> new ChannelGroupMenuItem(repository, channels, g))
                .toList();

        // チャネルグループを新規生成するメニューが初期化されていなければ作る
        if (createMenuItem == null) {
            createMenuItem = new ChannelGroupCreateMenuItem(repository, channels);
            createSeparator = new SeparatorMenuItem();
        }

        getItems().setAll(createMenuItem, createSeparator);
        getItems().addAll(groupMenuItems);
    }
}
