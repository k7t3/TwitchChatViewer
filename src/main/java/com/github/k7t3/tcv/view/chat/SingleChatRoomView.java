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
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.image.LazyImageView;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.FXCollections;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
import java.util.ResourceBundle;

public class SingleChatRoomView implements FxmlView<SingleChatRoomViewModel>, Initializable {

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

    private VirtualFlow<ChatDataViewModel, ChatDataCell> virtualFlow;

    @InjectViewModel
    private SingleChatRoomViewModel viewModel;

    private TwitchChannelViewModel channel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        channel = viewModel.getChannel();

        profileImageView = new LazyImageView();
        headerPane.add(profileImageView, 0, 0);
        profileImageView.lazyImageProperty().bind(channel.profileImageProperty());
        profileImageView.setFitWidth(PROFILE_IMAGE_SIZE);
        profileImageView.setFitHeight(PROFILE_IMAGE_SIZE);
        var clip = new Rectangle();
        clip.widthProperty().bind(profileImageView.fitWidthProperty());
        clip.heightProperty().bind(profileImageView.fitHeightProperty());
        clip.arcWidthProperty().bind(profileImageView.fitWidthProperty());
        clip.arcHeightProperty().bind(profileImageView.fitHeightProperty());
        profileImageView.setClip(clip);

        userNameLabel.textProperty().bind(channel.observableUserName());

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        // チャンネルグループに関するメニュー
        var repository = AppHelper.getInstance().getChannelGroupRepository();
        actionsMenuButton.getItems().add(1, new ChannelGroupMenu(repository, FXCollections.observableArrayList(channel)));

        popoutMenuItem.setOnAction(e -> viewModel.popOutAsFloatableStage());

        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), ChatDataCell::of);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // 配信していないときののイメージを更新する
        updateLiveState();
        channel.liveProperty().addListener((ob, o, n) -> updateLiveState());


        var roomStateNodes = new ChatRoomStateNodes();
        viewModel.getRoomStates().addListener((SetChangeListener<? super ChatRoomState>) (c) -> {
            if (c.wasAdded()) {
                var node = roomStateNodes.getIcon(c.getElementAdded());
                stateContainer.getChildren().add(node);
            }
            if (c.wasRemoved()) {
                var node = roomStateNodes.getIcon(c.getElementRemoved());
                stateContainer.getChildren().remove(node);
            }
        });
        viewModel.getRoomStates().stream()
                .map(roomStateNodes::getIcon)
                .forEach(stateContainer.getChildren()::add);

        // 自動スクロールと仮想フローにおける動作を初期化
        ChatRoomViewUtils.initializeVirtualFlowScrollActions(virtualFlow, viewModel.getChatDataList(), viewModel.autoScrollProperty());

        scrollToEnd.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        ChatRoomViewUtils.installStreamInfoPopOver(channel, profileImageView);

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
        JavaFXHelper.registerPseudoClass(headerPane, "selected", viewModel.selectedProperty());
        chatRoomControlsContainer.visibleProperty().bind(viewModel.selectModeProperty().not());
        chatRoomControlsContainer.managedProperty().bind(viewModel.selectModeProperty().not());

        selectedCheckBox.visibleProperty().bind(viewModel.selectModeProperty());
        selectedCheckBox.managedProperty().bind(viewModel.selectModeProperty());
    }

    private void updateLiveState() {
        if (!channel.isLive()) {
            var backgroundImage = channel.getOfflineImage();
            if (backgroundImage != null) {
                var bgSize = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, false);
                var bgImage = new BackgroundImage(backgroundImage, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, bgSize);
                var bg = new Background(bgImage);
                backgroundImageLayer.setBackground(bg);
            }
            profileImageView.setEffect(new SepiaTone());
        } else {
            backgroundImageLayer.setBackground(null);
            profileImageView.setEffect(null);
        }

        backgroundImageLayer.setVisible(!channel.isLive());
    }

}
