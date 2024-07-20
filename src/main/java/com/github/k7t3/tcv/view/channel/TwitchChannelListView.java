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

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
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

public class TwitchChannelListView implements FxmlView<TwitchChannelListViewModel>, Initializable {

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
        channels.setCellFactory(param -> new TwitchChannelListCell());
        channels.setItems(viewModel.getLoadedChannels());
        Bindings.size(channels.getItems()).addListener((ob, o, n) -> System.out.println("要素数: " + n));

        // チャンネルは複数選択することが可能
        channels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 選択しているブロードキャスターはView → ViewModelの一方向のみ
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        // ENTERキーの入力でチャットを開く
        channels.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;
            openChats();
        });

        // ダブルクリックでチャットを開く
        channels.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 2) return;
            openChats();
        });

        // コンテキストメニュー
        initContextMenu();

        var helper = AppHelper.getInstance();
        var channelGroupRepository = helper.getChannelGroupRepository();

        // チャンネルグループに関するメニューは遅延初期化
        channels.setOnContextMenuRequested(e -> {
            groupMenu = new ChannelGroupMenu(channelGroupRepository, viewModel.getSelectedChannels());
            groupMenu.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));
            groupMenu.refreshItems();
            contextMenu.getItems().add(1, groupMenu);

            // イベントを削除
            channels.setOnContextMenuRequested(null);
        });

        // Viewが選択している要素をバインド
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));
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
        open.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));

        var openBrowser = new MenuItem(Resources.getString("channel.open.browser"), new FontIcon(Feather.GLOBE));
        openBrowser.setOnAction(e -> openChannelPageOnBrowser());
        open.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));

        contextMenu = new ContextMenu(open, new SeparatorMenuItem(), openBrowser);

        channels.setContextMenu(contextMenu);
    }

    private void bindPreferences() {
        var preferences = AppPreferences.getInstance().getStatePreferences();
        onlyLiveMenuItem.selectedProperty().bindBidirectional(preferences.onlyLiveProperty());
        onlyFollowMenuItem.selectedProperty().bindBidirectional(preferences.onlyFollowsProperty());
    }

    private void openChats() {
        var selected = viewModel.getSelectedChannels();

        if (selected.isEmpty())
            return;

        // 選択数が多いときは本当に開くか確認する
        if (5 < selected.size()) {
            var content = Resources.getString("container.confirm.open.content.format").formatted(selected.size());
            var dialog = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
            dialog.initOwner(AppHelper.getInstance().getPrimaryStage());
            dialog.setTitle(Resources.getString("alert.title.confirmation"));
            dialog.setHeaderText(Resources.getString("container.confirm.open.header"));
            var result = dialog.showAndWait().orElse(ButtonType.NO);
            if (result != ButtonType.YES) {
                return;
            }
        }

        viewModel.joinChat();
    }

    private void openChannelPageOnBrowser() {
        var selected = viewModel.getSelectedChannels();

        if (selected.isEmpty())
            return;

        // 選択数が多いときは本当に開くか確認する
        if (5 < selected.size()) {
            var content = Resources.getString("container.confirm.open.content.format").formatted(selected.size());
            var dialog = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
            dialog.initOwner(AppHelper.getInstance().getPrimaryStage());
            dialog.setTitle(Resources.getString("alert.title.confirmation"));
            dialog.setHeaderText(Resources.getString("container.confirm.open.browser.header"));
            var result = dialog.showAndWait().orElse(ButtonType.NO);
            if (result != ButtonType.YES) {
                return;
            }
        }

        viewModel.openSelectedChannelPageOnBrowser();
    }

}
