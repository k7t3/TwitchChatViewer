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

package com.github.k7t3.tcv.view.channel;

import javafx.stage.Stage;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.view.core.OwnedDialog;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.List;
import java.util.function.Supplier;

public class TwitchChannelListView implements FxmlView<TwitchChannelListViewModel>, Initializable, AutoCloseable {
    private final Supplier<ChannelGroupRepository> groups;
    private final Supplier<Stage> owner;
    private final AppPreferences preferences;
    private final ViewResources owned = new ViewResources();
    private final OwnedDialog<ButtonType> confirmation;
    private boolean closed;
    private boolean opening;

    public TwitchChannelListView(Supplier<ChannelGroupRepository> groups, AppPreferences preferences, Supplier<Stage> owner) {
        this(groups, preferences, owner, () -> new Alert(Alert.AlertType.CONFIRMATION));
    }

    public TwitchChannelListView(Supplier<ChannelGroupRepository> groups, AppPreferences preferences, Supplier<Stage> owner,
                                 Supplier<? extends Dialog<ButtonType>> dialogs) {
        this.groups = java.util.Objects.requireNonNull(groups);
        this.owner = java.util.Objects.requireNonNull(owner);
        this.preferences = preferences;
        this.confirmation = new OwnedDialog<>(dialogs);
    }

    @FXML
    private Pane root;

    @FXML
    private ListView<TwitchChannelViewModel> channels;

    @FXML
    private CustomTextField searchField;

    @FXML
    private MenuButton optionMenuButton;

    @FXML
    private CheckMenuItem onlyLiveMenuItem;

    @FXML
    private CheckMenuItem onlyFollowMenuItem;

    @InjectViewModel
    private TwitchChannelListViewModel viewModel;

    private ContextMenu contextMenu;
    private ChannelGroupMenu groupMenu;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (closed) { detachControls(); return; }
        ListChangeListener<TwitchChannelViewModel> selectionChanged = change -> confirmation.cancel();
        viewModel.getSelectedChannels().addListener(selectionChanged);
        owned.add(() -> viewModel.getSelectedChannels().removeListener(selectionChanged));
        channels.setCellFactory(param -> {
            var cell = new TwitchChannelListCell(); owned.add(cell::close); return cell;
        });
        channels.setItems(viewModel.getLoadedChannels());

        // チャンネルは複数選択することが可能
        channels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 選択しているブロードキャスターはView → ViewModelの一方向のみ
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        // ENTERキーの入力でチャットを開く
        javafx.event.EventHandler<KeyEvent> keyboard = e -> {
            if (closed) return;
            if (e.getCode() != KeyCode.ENTER) return;
            openChats();
        };
        channels.addEventHandler(KeyEvent.KEY_PRESSED, keyboard);
        owned.add(() -> channels.removeEventHandler(KeyEvent.KEY_PRESSED, keyboard));

        // ダブルクリックでチャットを開く
        javafx.event.EventHandler<MouseEvent> mouse = e -> {
            if (closed) return;
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 2) return;
            openChats();
        };
        channels.addEventHandler(MouseEvent.MOUSE_PRESSED, mouse);
        owned.add(() -> channels.removeEventHandler(MouseEvent.MOUSE_PRESSED, mouse));

        // コンテキストメニュー
        initContextMenu();

        var channelGroupRepository = groups.get();

        // チャンネルグループに関するメニューは遅延初期化
        channels.setOnContextMenuRequested(e -> {
            if (closed || groupMenu != null) return;
            groupMenu = new ChannelGroupMenu(channelGroupRepository, viewModel.getSelectedChannels());
            owned.add(groupMenu::close);
            groupMenu.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));
            groupMenu.refreshItems();
            if (closed) return;
            contextMenu.getItems().add(1, groupMenu);

            // イベントを削除
            channels.setOnContextMenuRequested(null);
        });

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> { if (!closed) viewModel.setFilter(null); });
        owned.add(() -> clearIcon.setOnMouseClicked(null));
        clearIcon.setCursor(Cursor.HAND);

        searchField.textProperty().bindBidirectional(viewModel.filterProperty());
        searchField.setRight(clearIcon);
        searchField.setLeft(new FontIcon(Feather.SEARCH));

        // オプションメニュー
        optionMenuButton.getStyleClass().addAll(Styles.BUTTON_ICON, Tweaks.NO_ARROW);

        // 配信中のみ
        onlyLiveMenuItem.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());

        // フォローのみ
        onlyFollowMenuItem.selectedProperty().bindBidirectional(viewModel.onlyFollowProperty());

        // 設定とバインド
        bindPreferences();
    }

    private void initContextMenu() {
        var open = new MenuItem(Resources.getString("channel.list.open"));
        open.setOnAction(e -> openChats());
        owned.add(() -> { open.disableProperty().unbind(); open.setOnAction(null); });
        open.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));

        var openBrowser = new MenuItem(Resources.getString("channel.open.browser"), new FontIcon(Feather.GLOBE));
        openBrowser.setOnAction(e -> openChannelPageOnBrowser());
        owned.add(() -> { openBrowser.disableProperty().unbind(); openBrowser.setOnAction(null); });
        openBrowser.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));

        contextMenu = new ContextMenu(open, new SeparatorMenuItem(), openBrowser);

        channels.setContextMenu(contextMenu);
    }

    private void bindPreferences() {
        var preferences = this.preferences.getStatePreferences();
        onlyLiveMenuItem.selectedProperty().bindBidirectional(preferences.onlyLiveProperty());
        onlyFollowMenuItem.selectedProperty().bindBidirectional(preferences.onlyFollowsProperty());
    }

    private void openChats() {
        openSelectedChannels(false);
    }

    private void openChannelPageOnBrowser() {
        openSelectedChannels(true);
    }

    private void openSelectedChannels(boolean browser) {
        if (closed || opening || viewModel.isClosed()) return;
        opening = true;
        try {
            var selected = List.copyOf(viewModel.getSelectedChannels());
            if (selected.isEmpty()) return;
            if (5 < selected.size()) {
                var result = confirmation.show(dialog -> {
                    dialog.getDialogPane().setContentText(Resources.getString("container.confirm.open.content.format").formatted(selected.size()));
                    dialog.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    dialog.initOwner(owner.get());
                    dialog.setTitle(Resources.getString("alert.title.confirmation"));
                    dialog.setHeaderText(Resources.getString(browser ? "container.confirm.open.browser.header" : "container.confirm.open.header"));
                });
                if (result.orElse(ButtonType.NO) != ButtonType.YES) return;
            }
            if (closed || viewModel.isClosed() || !selected.equals(viewModel.getSelectedChannels())) return;
            if (browser) viewModel.openSelectedChannelPageOnBrowser(); else viewModel.joinChat();
        } finally { opening = false; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        com.github.k7t3.tcv.lifecycle.CloseActions.runAll(
                confirmation::close, owned::close, this::detachControls);
    }

    private void detachControls() {
        if (contextMenu != null) { contextMenu.hide(); contextMenu.getItems().clear(); }
        if (channels != null && viewModel != null) {
            Bindings.unbindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());
            channels.setItems(null);
            channels.setCellFactory(null);
            channels.setContextMenu(null);
            channels.setOnContextMenuRequested(null);
        }
        if (searchField != null && viewModel != null) {
            searchField.textProperty().unbindBidirectional(viewModel.filterProperty());
            searchField.setRight(null); searchField.setLeft(null);
        }
        if (optionMenuButton != null) optionMenuButton.hide();
        var state = preferences.getStatePreferences();
        if (onlyLiveMenuItem != null) {
            if (viewModel != null) onlyLiveMenuItem.selectedProperty().unbindBidirectional(viewModel.onlyLiveProperty());
            onlyLiveMenuItem.selectedProperty().unbindBidirectional(state.onlyLiveProperty());
        }
        if (onlyFollowMenuItem != null) {
            if (viewModel != null) onlyFollowMenuItem.selectedProperty().unbindBidirectional(viewModel.onlyFollowProperty());
            onlyFollowMenuItem.selectedProperty().unbindBidirectional(state.onlyFollowsProperty());
        }
    }

}
