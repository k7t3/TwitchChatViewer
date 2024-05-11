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
