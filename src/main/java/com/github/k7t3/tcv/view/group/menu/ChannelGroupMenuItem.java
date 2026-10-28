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
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckMenuItem;

import java.util.List;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;

class ChannelGroupMenuItem extends CheckMenuItem implements AutoCloseable {
    private BooleanBinding membership;
    private final ChangeListener<Boolean> membershipChanged = (observable, previous, current) -> setSelected(current);
    private boolean closed;

    @Override public void close() {
        if (closed) return;
        closed = true;
        setOnAction(null);
        textProperty().unbind();
        if (membership != null) {
            membership.removeListener(membershipChanged);
            membership.dispose();
            membership = null;
        }
    }

    private final ChannelGroupRepository repository;

    private final ObservableList<TwitchChannelViewModel> channels;

    private final ChannelGroup group;

    ChannelGroupMenuItem(
            ChannelGroupRepository repository,
            ObservableList<TwitchChannelViewModel> channels,
            ChannelGroup group
    ) {
        this.repository = repository;
        this.channels = channels;
        this.group = group;
        init();
    }

    private void init() {
        // メニューの名前はグループ名
        textProperty().bind(group.nameProperty());

        // グループにチャンネルが登録されたときに選択状態を更新するリスナを追加
        var groupChannels = group.getChannels();
        membership = Bindings.createBooleanBinding(this::containsAllChannels, groupChannels, channels);
        membership.addListener(membershipChanged);

        // 初期状態を設定
        setSelected(containsAllChannels());

        // メニューアクションの定義
        setOnAction(this::action);
    }

    private boolean containsAllChannels() {
        return group.getChannels().containsAll(channels);
    }

    private void action(ActionEvent e) {
        e.consume();
        if (closed) return;
        var selected = List.copyOf(this.channels);
        if (selected.isEmpty()) return;

        // グループに登録されているチャンネルのリスト
        var channels = group.getChannels();

        if (isSelected()) {

            // チャンネル一覧に追加
            for (var channel : selected) {
                if (!channels.contains(channel)) {
                    channels.add(channel);
                }
            }

            // セーブ
            repository.saveAsync(group);

        } else {

            // チャンネルの一覧から除去
            selected.forEach(channels::remove);

            if (channels.isEmpty()) {
                // 登録されているチャンネルが空になったら削除する
                repository.removeAsync(group);
            } else {
                repository.saveAsync(group);
            }

        }
    }

}
