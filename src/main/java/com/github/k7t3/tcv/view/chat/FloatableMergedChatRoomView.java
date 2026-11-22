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
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.view.core.FloatableStage;
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
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;


public class FloatableMergedChatRoomView implements FxmlView<MergedChatRoomViewModel>, Initializable, AutoCloseable {
    private final ChannelGroupRepository groups;
    private final ChatPreferences preferences;
    private final WindowBoundsService boundsService;
    private final ViewResources owned = new ViewResources();

    /** 共有サービスを借用し、窓と画面自身の監視だけを所有する。 */
    public FloatableMergedChatRoomView(ChannelGroupRepository groups, ChatPreferences preferences,
                                      WindowBoundsService boundsService) {
        this.groups = java.util.Objects.requireNonNull(groups);
        this.preferences = java.util.Objects.requireNonNull(preferences);
        this.boundsService = java.util.Objects.requireNonNull(boundsService);
    }
    @Override public void close() { owned.close(); }


    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private ToolBar profileImageContainer;

    @FXML
    private CheckMenuItem alwaysOnTopMenuItem;

    @FXML
    private CheckMenuItem autoScrollMenuItem;

    @FXML
    private MenuItem restoreMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuButton menuButton;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;

    @FXML
    private Slider opacitySlider;

    @InjectViewModel
    private MergedChatRoomViewModel viewModel;


    private FloatableStage floatableStage;

    private Map<TwitchChannelViewModel, Node> profileImageNodes;
    private final Map<TwitchChannelViewModel, ViewResources> profiles = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (owned.isClosed()) return;

        floatableStage = new FloatableStage();
        owned.add(() -> floatableStage.setScene(null));
        owned.add(() -> floatableStage.setContent(null));
        owned.add(floatableStage::close);
        floatableStage.onClose(this::close);
        EventHandler<WindowEvent> hidden = event -> close();
        floatableStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, hidden);
        owned.add(() -> floatableStage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, hidden));
        floatableStage.setOnCloseRequest(event -> {
            event.consume();
            if (owned.isClosed()) return;
            floatableStage.close(); viewModel.leaveChatAsync();
        });
        owned.add(() -> floatableStage.setOnCloseRequest(null));
        var bounds = ChatRoomViewUtils.initializeFloatableStage(floatableStage, viewModel, boundsService); owned.add(bounds::close);
        if (owned.isClosed()) return;

        var prefs = preferences;
        owned.add(() -> {
            autoScrollMenuItem.selectedProperty().unbindBidirectional(viewModel.autoScrollProperty());
            floatableStage.backgroundOpacityProperty().unbindBidirectional(prefs.floatingChatOpacityProperty());
            opacitySlider.valueProperty().unbindBidirectional(floatableStage.backgroundOpacityProperty());
            alwaysOnTopMenuItem.selectedProperty().unbindBidirectional(prefs.floatingChatAlwaysTopProperty());
            closeMenuItem.setOnAction(null); restoreMenuItem.setOnAction(null); menuButton.hide();
        });

        menuButton.getStyleClass().addAll(Tweaks.NO_ARROW, Styles.BUTTON_ICON);

        // チャンネルグループに関するメニュー
        if (owned.isClosed()) return;
        var groupMenu = new ChannelGroupMenu(groups, viewModel.getChannels());
        owned.add(groupMenu::close);
        if (owned.isClosed()) return;
        menuButton.getItems().addAll(0, List.of(groupMenu, new SeparatorMenuItem()));

        // 閉じるボタン
        closeMenuItem.setOnAction(e -> {
            floatableStage.close();
            viewModel.leaveChatAsync();
        });

        // 元に戻すボタン
        restoreMenuItem.setOnAction(e -> {
            floatableStage.close();
            viewModel.restoreToContainer();
        });

        // チャット
        var messages = ChatRoomViewUtils.installMessages(chatDataContainer, viewModel, true);
        owned.add(messages::unsubscribe);
        if (owned.isClosed()) return;

        autoScrollMenuItem.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        // 透過度
        floatableStage.backgroundOpacityProperty().bindBidirectional(prefs.floatingChatOpacityProperty());
        opacitySlider.valueProperty().bindBidirectional(floatableStage.backgroundOpacityProperty());

        // 常に最前面に表示
        alwaysOnTopMenuItem.selectedProperty().bindBidirectional(prefs.floatingChatAlwaysTopProperty());
        ChangeListener<Boolean> onTop = (ob, oldValue, value) -> { if (!owned.isClosed()) floatableStage.setAlwaysOnTop(value); };
        alwaysOnTopMenuItem.selectedProperty().addListener(onTop); owned.add(() -> alwaysOnTopMenuItem.selectedProperty().removeListener(onTop));
        floatableStage.setAlwaysOnTop(alwaysOnTopMenuItem.isSelected());

        profileImageNodes = new HashMap<>();
        owned.add(() -> {
            var snapshot = List.copyOf(profiles.values()); profiles.clear(); profileImageNodes.clear(); profileImageContainer.getItems().clear();
            CloseActions.runAll(snapshot.stream().map(profile -> (Runnable) profile::close).toArray(Runnable[]::new));
        });

        for (var channel : List.copyOf(viewModel.getChannels().keySet())) {
            if (owned.isClosed()) return;
            var node = createProfileImageView(channel);
            profileImageNodes.put(channel, node);
            profileImageContainer.getItems().add(node);
        }
        MapChangeListener<TwitchChannelViewModel, SingleChatRoomViewModel> changed = this::channelChanged;
        viewModel.getChannels().addListener(changed);
        owned.add(() -> viewModel.getChannels().removeListener(changed));
    }

    public FloatableStage getFloatableStage() {
        return floatableStage;
    }

    /**
     * ブロードキャスターのプロファイルイメージNode
     */
    private Node createProfileImageView(TwitchChannelViewModel channel) {
        var profile = new ViewResources();
        var previous = profiles.put(channel, profile);
        if (previous != null) previous.close();
        var imageView = new ChannelProfileView(channel, PROFILE_IMAGE_SIZE);
        profile.add(imageView::close);

        return imageView;
    }

    /**
     * 監視するチャンネルの一覧が更新されたときにViewに反映するメソッド
     */
    private void channelChanged(MapChangeListener.Change<? extends TwitchChannelViewModel, ? extends SingleChatRoomViewModel> change) {
        if (owned.isClosed()) return;
        if (change.wasRemoved()) {
            var node = profileImageNodes.remove(change.getKey());
            if (node != null) profileImageContainer.getItems().remove(node);
            var profile = profiles.remove(change.getKey()); if (profile != null) profile.close();
        }
        if (change.wasAdded()) {
            var channel = change.getKey();
            var node = createProfileImageView(channel);
            profileImageNodes.put(channel, node); profileImageContainer.getItems().add(node);
        }
    }

}
