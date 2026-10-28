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
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import com.github.k7t3.tcv.view.image.LazyImageView;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;


public class SingleChatRoomView implements FxmlView<SingleChatRoomViewModel>, Initializable, AutoCloseable {
    private final ChannelGroupRepository groups;
    private final ViewResources owned = new ViewResources();

    /** 共有のグループ管理を借用し、この画面自身の監視だけを所有する。 */
    public SingleChatRoomView(ChannelGroupRepository groups) {
        this.groups = java.util.Objects.requireNonNull(groups);
    }
    @Override public void close() { owned.close(); }


    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private GridPane headerPane;

    private LazyImageView profileImageView;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label streamTitleLabel;

    @FXML
    private MenuButton actionsMenuButton;

    @FXML
    private CheckMenuItem selectedMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem popoutMenuItem;

    @FXML
    private Pane chatRoomControlsContainer;

    @FXML
    private CheckBox selectedCheckBox;

    @FXML
    private Pane stateContainer;

    @FXML
    private ToggleButton scrollToEnd;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;


    @InjectViewModel
    private SingleChatRoomViewModel viewModel;

    private TwitchChannelViewModel channel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (owned.isClosed()) return;
        owned.add(() -> {
            scrollToEnd.selectedProperty().unbindBidirectional(viewModel.autoScrollProperty());
            selectedMenuItem.selectedProperty().unbindBidirectional(viewModel.selectedProperty());
            selectedCheckBox.selectedProperty().unbindBidirectional(viewModel.selectedProperty());
            chatRoomControlsContainer.visibleProperty().unbind(); chatRoomControlsContainer.managedProperty().unbind();
            selectedCheckBox.visibleProperty().unbind(); selectedCheckBox.managedProperty().unbind();
            headerPane.setOnMouseClicked(null); closeMenuItem.setOnAction(null); popoutMenuItem.setOnAction(null);
            actionsMenuButton.hide();
            userNameLabel.textProperty().unbind(); streamTitleLabel.textProperty().unbind(); streamTitleLabel.visibleProperty().unbind();
            if (streamTitleLabel.getTooltip() != null) streamTitleLabel.getTooltip().textProperty().unbind();
            streamTitleLabel.setTooltip(null);
        });
        channel = viewModel.getChannel();
        if (owned.isClosed()) return;

        var profile = new ChannelProfileView(channel, PROFILE_IMAGE_SIZE);
        profileImageView = profile;
        owned.add(profile::close);
        if (owned.isClosed()) return;
        headerPane.add(profileImageView, 0, 0);

        userNameLabel.textProperty().bind(channel.observableUserName());

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        // チャンネルグループに関するメニュー
        if (owned.isClosed()) return;
        var groupMenu = new ChannelGroupMenu(groups, FXCollections.observableArrayList(channel));
        owned.add(groupMenu::close);
        if (owned.isClosed()) return;
        actionsMenuButton.getItems().add(1, groupMenu);

        popoutMenuItem.setOnAction(e -> viewModel.popOutAsFloatableStage());

        var messages = ChatRoomViewUtils.installMessages(chatDataContainer, viewModel, false);
        owned.add(messages::unsubscribe);
        if (owned.isClosed()) return;

        var background = ChatRoomViewUtils.offlineBackground(channel);
        owned.add(() -> { backgroundImageLayer.backgroundProperty().unbind(); backgroundImageLayer.setBackground(null); background.dispose(); });
        backgroundImageLayer.backgroundProperty().bind(background);
        updateLiveState();
        ChangeListener<Boolean> liveChanged = (ob, oldValue, live) -> { if (!owned.isClosed()) updateLiveState(); };
        channel.liveProperty().addListener(liveChanged); owned.add(() -> channel.liveProperty().removeListener(liveChanged));


        var roomStateNodes = new ChatRoomStateNodes();
        SetChangeListener<ChatRoomState> statesChanged = c -> {
            if (owned.isClosed()) return;
            if (c.wasAdded()) {
                var node = roomStateNodes.getIcon(c.getElementAdded());
                if (node != null) stateContainer.getChildren().add(node);
            }
            if (c.wasRemoved()) {
                var node = roomStateNodes.getIcon(c.getElementRemoved());
                stateContainer.getChildren().remove(node);
            }
        };
        viewModel.getRoomStates().addListener(statesChanged);
        owned.add(() -> { viewModel.getRoomStates().removeListener(statesChanged); stateContainer.getChildren().clear(); });
        viewModel.getRoomStates().stream()
                .map(roomStateNodes::getIcon)
                .filter(java.util.Objects::nonNull)
                .forEach(stateContainer.getChildren()::add);

        scrollToEnd.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());


        streamTitleLabel.visibleProperty().bind(channel.liveProperty());
        streamTitleLabel.textProperty().bind(channel.observableStreamTitle());
        streamTitleLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        var titleTooltip = new Tooltip();
        titleTooltip.textProperty().bind(streamTitleLabel.textProperty());
        streamTitleLabel.setTooltip(titleTooltip);

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
    }

    private void updateLiveState() {
        backgroundImageLayer.setVisible(!channel.isLive());
    }

}
