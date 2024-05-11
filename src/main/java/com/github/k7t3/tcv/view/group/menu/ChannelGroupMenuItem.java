package com.github.k7t3.tcv.view.group.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.CheckMenuItem;

import java.util.HashSet;

class ChannelGroupMenuItem extends CheckMenuItem {

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
        Bindings.createBooleanBinding(this::containsAllChannels, groupChannels)
                .addListener((ob, o, n) -> setSelected(n));

        // 初期状態を設定
        setSelected(containsAllChannels());

        // メニューアクションの定義
        addEventHandler(ActionEvent.ACTION, this::action);
    }

    private boolean containsAllChannels() {
        return new HashSet<>(group.getChannels()).containsAll(channels);
    }

    private void action(ActionEvent e) {
        e.consume();

        // グループに登録されているチャンネルのリスト
        var channels = group.getChannels();

        if (isSelected()) {

            // チャンネル一覧に追加
            for (var channel : this.channels) {
                if (!channels.contains(channel)) {
                    channels.add(channel);
                }
            }

            // セーブ
            repository.saveAsync(group);

        } else {

            // チャンネルの一覧から除去
            channels.removeAll(this.channels);

            if (channels.isEmpty()) {
                // 登録されているチャンネルが空になったら削除する
                repository.removeAsync(group);
            } else {
                repository.saveAsync(group);
            }

        }
    }

}
