package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class FollowChannelsView implements FxmlView<FollowChannelsViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private ListView<TwitchChannelViewModel> channels;

    @FXML
    private CustomTextField searchField;

    @InjectViewModel
    private FollowChannelsViewModel viewModel;

    private ContextMenu contextMenu;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var helper = AppHelper.getInstance();

        // 認証されていないときはrootから無効化
        root.disableProperty().bind(helper.authorizedProperty().not());

        channels.setCellFactory(param -> new FollowChannelListCell(viewModel.visibleFullyProperty()));
        channels.disableProperty().bind(viewModel.loadedProperty().not());
        channels.setItems(viewModel.getFollowChannels());

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

        // チャンネルグループに関するメニューは遅延初期化
        channels.setOnContextMenuRequested(e -> {
            var repository = AppHelper.getInstance().getChannelGroupRepository();
            groupMenu = new ChannelGroupMenu(repository, viewModel.getSelectedChannels());
            groupMenu.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));
            groupMenu.refreshItems();
            contextMenu.getItems().add(groupMenu);

            // イベントを削除
            channels.setOnContextMenuRequested(null);
        });

        // Viewが選択している要素をバインド
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));

        searchField.textProperty().bindBidirectional(viewModel.filterProperty());
        searchField.disableProperty().bind(viewModel.loadedProperty().not());
        searchField.setRight(clearIcon);
        searchField.setLeft(new FontIcon(Feather.SEARCH));
    }

    private ChannelGroupMenu groupMenu;

    private void initContextMenu() {
        var open = new MenuItem(Resources.getString("channel.list.open"));
        open.setOnAction(e -> openChats());
        open.disableProperty().bind(Bindings.isEmpty(viewModel.getSelectedChannels()));

        contextMenu = new ContextMenu(open);

        channels.setContextMenu(contextMenu);
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
            dialog.setTitle("CONFIRMATION");
            dialog.setHeaderText(Resources.getString("container.confirm.open.header"));
            var result = dialog.showAndWait().orElse(ButtonType.NO);
            if (result != ButtonType.YES) {
                return;
            }
        }

        viewModel.joinChat();
    }

}
