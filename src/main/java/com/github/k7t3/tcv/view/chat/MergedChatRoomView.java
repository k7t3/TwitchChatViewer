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

package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;


public class MergedChatRoomView implements FxmlView<MergedChatRoomViewModel>, Initializable, AutoCloseable {
    private final ChannelGroupRepository groups;
    private final ViewResources owned = new ViewResources();

    /** 共有のグループ管理を借用し、この画面自身の監視だけを所有する。 */
    public MergedChatRoomView(ChannelGroupRepository groups) {
        this.groups = java.util.Objects.requireNonNull(groups);
    }
    @Override public void close() { owned.close(); }


    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private Pane headerPane;

    @FXML
    private ToolBar profileImageContainer;

    @FXML
    private MenuButton actionsMenuButton;

    @FXML
    private CheckMenuItem selectedMenuItem;

    @FXML
    private MenuItem separateAllMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem popoutMenuItem;

    @FXML
    private ToggleButton scrollToEnd;

    @FXML
    private Pane chatRoomControlsContainer;

    @FXML
    private CheckBox selectedCheckBox;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;


    @InjectViewModel
    private MergedChatRoomViewModel viewModel;

    private Map<TwitchChannelViewModel, Node> profileImageNodes;
    private final Map<TwitchChannelViewModel, ViewResources> profiles = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (owned.isClosed()) return;
        owned.add(() -> {
            scrollToEnd.selectedProperty().unbindBidirectional(viewModel.autoScrollProperty());
            selectedMenuItem.selectedProperty().unbindBidirectional(viewModel.selectedProperty());
            selectedCheckBox.selectedProperty().unbindBidirectional(viewModel.selectedProperty());
            chatRoomControlsContainer.visibleProperty().unbind(); chatRoomControlsContainer.managedProperty().unbind();
            selectedCheckBox.visibleProperty().unbind(); selectedCheckBox.managedProperty().unbind();
            headerPane.setOnMouseClicked(null); closeMenuItem.setOnAction(null); popoutMenuItem.setOnAction(null);
            actionsMenuButton.hide();
            separateAllMenuItem.setOnAction(null);
        });
        profileImageNodes = new HashMap<>();
        owned.add(() -> {
            var snapshot = List.copyOf(profiles.values()); profiles.clear(); profileImageNodes.clear(); profileImageContainer.getItems().clear();
            CloseActions.runAll(snapshot.stream().map(profile -> (Runnable) profile::close).toArray(Runnable[]::new));
        });

        for (var channel : List.copyOf(viewModel.getChannels().keySet())) {
            if (owned.isClosed()) return;
            var chatRoom = viewModel.getChannels().get(channel);
            var node = createProfileImageView(channel, chatRoom);
            profileImageNodes.put(channel, node);
            profileImageContainer.getItems().add(node);
        }

        // チャンネルの増減
        MapChangeListener<TwitchChannelViewModel, SingleChatRoomViewModel> changed = this::channelChanged;
        viewModel.getChannels().addListener(changed);
        owned.add(() -> viewModel.getChannels().removeListener(changed));

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        // チャンネルグループに関するメニュー
        if (owned.isClosed()) return;
        var groupMenu = new ChannelGroupMenu(groups, viewModel.getChannels());
        owned.add(groupMenu::close);
        if (owned.isClosed()) return;
        actionsMenuButton.getItems().add(1, groupMenu);

        popoutMenuItem.setOnAction(e -> viewModel.popOutAsFloatableStage());

        var messages = ChatRoomViewUtils.installMessages(chatDataContainer, viewModel, true);
        owned.add(messages::unsubscribe);
        if (owned.isClosed()) return;

        scrollToEnd.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        // ヘッダをダブルクリックすると選択状態を切り替える
        headerPane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                e.consume();
                viewModel.setSelected(!viewModel.isSelected());
            }
        });

        selectedMenuItem.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        selectedCheckBox.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        ChangeListener<Boolean> selection = (ob, oldValue, selected) -> { if (!owned.isClosed()) JavaFXHelper.updatePseudoClass(headerPane, "selected", selected); };
        viewModel.selectedProperty().addListener(selection); owned.add(() -> viewModel.selectedProperty().removeListener(selection));
        JavaFXHelper.updatePseudoClass(headerPane, "selected", viewModel.selectedProperty().get());
        chatRoomControlsContainer.visibleProperty().bind(viewModel.selectModeProperty().not());
        chatRoomControlsContainer.managedProperty().bind(viewModel.selectModeProperty().not());

        selectedCheckBox.visibleProperty().bind(viewModel.selectModeProperty());
        selectedCheckBox.managedProperty().bind(viewModel.selectModeProperty());

        separateAllMenuItem.setOnAction(e -> viewModel.separateAll());
    }

    private Node createProfileImageView(TwitchChannelViewModel channel, SingleChatRoomViewModel chatRoom) {
        var profile = new ViewResources();
        var previous = profiles.put(channel, profile);
        if (previous != null) previous.close();
        var imageView = new ChannelProfileView(channel, PROFILE_IMAGE_SIZE);
        profile.add(imageView::close);

        var separate = new MenuItem(Resources.getString("chat.separate"), new FontIcon(Feather.EXTERNAL_LINK));
        separate.setOnAction(e -> viewModel.separateChatRoom(chatRoom));

        var close = new MenuItem(Resources.getString("menu.close"), new FontIcon(Feather.X));
        close.setOnAction(e -> viewModel.closeChatRoom(chatRoom));

        var menuButton = new MenuButton(null, imageView, separate, new SeparatorMenuItem(), close);
        menuButton.accessibleTextProperty().bind(channel.observableUserName());
        profile.add(() -> menuButton.accessibleTextProperty().unbind());
        menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        menuButton.getStyleClass().addAll(Tweaks.NO_ARROW);
        menuButton.setPrefHeight(Region.USE_COMPUTED_SIZE);

        var hover = ChatRoomViewUtils.installStreamInfoPopOver(channel, menuButton); profile.add(hover::unsubscribe);
        profile.add(() -> { menuButton.hide(); menuButton.setGraphic(null); separate.setOnAction(null); close.setOnAction(null); menuButton.getItems().clear(); });

        // ChatRoomViewUtils.installStreamInfoPopOverが付与するイベントを上書き
        menuButton.setOnMousePressed(null);

        return menuButton;
    }

    private void channelChanged(MapChangeListener.Change<? extends TwitchChannelViewModel, ? extends SingleChatRoomViewModel> change) {
        if (owned.isClosed()) return;
        if (change.wasRemoved()) {
            var node = profileImageNodes.remove(change.getKey());
            if (node != null) profileImageContainer.getItems().remove(node);
            var profile = profiles.remove(change.getKey()); if (profile != null) profile.close();
        }
        if (change.wasAdded()) {
            var channel = change.getKey();
            var node = createProfileImageView(channel, change.getValueAdded());
            profileImageNodes.put(channel, node); profileImageContainer.getItems().add(node);
        }
    }

}
