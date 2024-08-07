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

package com.github.k7t3.tcv.view.group;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.view.image.LazyImageView;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import com.github.k7t3.tcv.view.channel.LiveInfoPopup;
import com.github.k7t3.tcv.view.channel.menu.OpenBrowserMenuItem;
import com.github.k7t3.tcv.view.channel.menu.OpenChatMenuItem;
import com.github.k7t3.tcv.view.control.EditableLabel;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.function.Supplier;

public class ChannelGroupListCell extends Card implements Cell<ChannelGroup, Region> {

    private static final String STYLE_CLASS = "channel-group-cell";
    private static final String TILE_STYLE_CLASS = "channel-group-tile";

    private static final double PROFILE_IMAGE_WIDTH = 48;
    private static final double PROFILE_IMAGE_HEIGHT = 48;

    private final EditableLabel title = new EditableLabel();
    private final ToggleButton pin = new ToggleButton(null, new FontIcon());
    private final EditableLabel comment = new EditableLabel();
    private final TilePane tilePane = new TilePane();

    private final GeneralPreferences generalPrefs;
    private final ChannelGroup group;
    private final ChannelGroupListViewModel viewModel;
    private final Supplier<VirtualFlow<ChannelGroup, ChannelGroupListCell>> vfInjector;

    public ChannelGroupListCell(
            GeneralPreferences generalPrefs,
            ChannelGroup group,
            ChannelGroupListViewModel viewModel,
            Supplier<VirtualFlow<ChannelGroup, ChannelGroupListCell>> vfInjector
    ) {
        this.generalPrefs = generalPrefs;
        this.group = group;
        this.viewModel = viewModel;
        this.vfInjector = vfInjector;
        init();
        update();
    }

    private void init() {
        getStyleClass().add(STYLE_CLASS);
        setBody(tilePane);

        var headerPane = new HBox();

        title.getStyleClass().add(Styles.TEXT_CAPTION);
        title.setTooltip(new Tooltip(Resources.getString("group.tooltip.title")));
        title.getStyleClass().add("title");
        HBox.setHgrow(title, Priority.ALWAYS);

        pin.getStyleClass().addAll(Styles.BUTTON_ICON);
        pin.setTooltip(new Tooltip(Resources.getString("group.pin.tooltip")));

        headerPane.getChildren().addAll(title, pin);
        setHeader(headerPane);

        comment.setTooltip(new Tooltip(Resources.getString("group.tooltip.comment")));
        comment.getStyleClass().add("comment");
        comment.getStyleClass().add(Styles.TEXT_SMALL);
        comment.setPromptText(Resources.getString("group.comment"));
        setSubHeader(comment);

        // ****************************************
        // チャットを開くボタン
        // ****************************************
        var openSeparatedItem = new MenuItem(Resources.getString("group.button.open.separated.chat"));
        openSeparatedItem.setOnAction(e -> {
            e.consume();

            var openType = MultipleChatOpenType.SEPARATED;
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });
        var openMergedItem = new MenuItem(Resources.getString("group.button.open.merged.chat"));
        openMergedItem.setOnAction(e -> {
            e.consume();

            var openType = MultipleChatOpenType.MERGED;
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });
        var openChatButton = new SplitMenuButton(openSeparatedItem, openMergedItem);
        openChatButton.setText(Resources.getString("group.button.open.chat"));
        openChatButton.setGraphic(new FontIcon(FontAwesomeRegular.COMMENT_DOTS));
        openChatButton.getStyleClass().add(Styles.ACCENT);
        openChatButton.setOnAction(e -> {
            e.consume();

            var openType = generalPrefs.getMultipleOpenType();
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });

        // ****************************************
        // 削除ボタン
        // ****************************************
        var deleteButton = new Button(Resources.getString("group.button.delete"), new FontIcon(Feather.TRASH));
        deleteButton.getStyleClass().addAll(Styles.DANGER);
        deleteButton.setOnAction(e -> {
            e.consume();

            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(getScene().getWindow());
            alert.setTitle(Resources.getString("group.dialog.delete.title"));
            alert.setHeaderText(Resources.getString("group.dialog.delete.header"));
            alert.setContentText(Resources.getString("group.dialog.delete.content").formatted(group.getName()));
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }

            setDisable(true);
            var t = viewModel.delete(group);
            t.setFinally(() -> setDisable(false));
        });

        var footer = new HBox(openChatButton, deleteButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setSpacing(10);
        footer.setPadding(new Insets(0d, 4d, 4d, 2d));
        setFooter(footer);

        pin.setOnAction(e -> {
            setDisable(true);
            var t = viewModel.update(group);
            t.setFinally(() -> setDisable(false));
            t.onDone(() -> vfInjector.get().show(0));
        });
        title.setOnEditCommit(e -> {
            setDisable(true);
            var t = viewModel.update(group);
            t.setFinally(() -> setDisable(false));
        });
        comment.setOnEditCommit(e -> {
            setDisable(true);
            var t = viewModel.update(group);
            t.setFinally(() -> setDisable(false));
        });
    }

    @Override
    public Region getNode() {
        return this;
    }

    @Override
    public void dispose() {
        title.textProperty().unbindBidirectional(group.nameProperty());
        comment.textProperty().unbindBidirectional(group.commentProperty());
    }

    public void update() {
        title.textProperty().bindBidirectional(group.nameProperty());
        comment.textProperty().bindBidirectional(group.commentProperty());
        tilePane.getChildren().clear();
        tilePane.setHgap(4);
        tilePane.setVgap(2);
        pin.selectedProperty().bindBidirectional(group.pinnedProperty());

        group.getChannels().stream()
                .sorted(Comparator.comparing(TwitchChannelViewModel::getUserName))
                .sorted((c1, c2) -> Boolean.compare(c2.isLive(), c1.isLive()))
                .map(this::createChannelNode)
                .forEach(tilePane.getChildren()::add);
    }

    private Node createChannelNode(TwitchChannelViewModel channel) {
        var tile = new Tile();
        tile.getStyleClass().add(TILE_STYLE_CLASS);
        tile.setPrefWidth(USE_COMPUTED_SIZE);
        tile.titleProperty().bind(channel.liveProperty().map(live -> live ? "LIVE" : null));
        tile.descriptionProperty().bind(channel.observableUserName());

        // ツールチップに配信のタイトルを割り当て
        var tooltip = new Tooltip();
        tooltip.textProperty().bind(channel.observableStreamTitle());

        // チャンネルページをブラウザで開く
        var openPageMenuItem = new OpenBrowserMenuItem(channel);

        // グループから削除
        var removeMenuItem = new MenuItem(Resources.getString("group.button.remove"), new FontIcon(Feather.X));
        removeMenuItem.setOnAction(e -> {
            group.getChannels().remove(channel);
            var repository = AppHelper.getInstance().getChannelGroupRepository();
            setDisable(true);
            var t = repository.saveAsync(group);
            t.onDone(() -> tilePane.getChildren().remove(tile));
            t.setFinally(() -> setDisable(false));
        });

        // チャットを開く
        var openChatMenuItem = new OpenChatMenuItem(channel);

        // タイルのアクション(MenuButton)
        var menuButton = new MenuButton(null, new FontIcon(Feather.MORE_VERTICAL));
        menuButton.getStyleClass().addAll(Styles.BUTTON_ICON, Tweaks.NO_ARROW);
        menuButton.getItems().addAll(
                openChatMenuItem,
                new SeparatorMenuItem(),
                removeMenuItem,
                new SeparatorMenuItem(),
                openPageMenuItem
        );
        tile.setAction(menuButton);

        var imageView = new LazyImageView();
        imageView.lazyImageProperty().bind(channel.profileImageProperty());
        imageView.setFitWidth(PROFILE_IMAGE_WIDTH);
        imageView.setFitHeight(PROFILE_IMAGE_HEIGHT);
        var clip = new Rectangle();
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        clip.arcWidthProperty().bind(imageView.fitWidthProperty());
        clip.arcHeightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);
        tile.setGraphic(imageView);
        installStreamInfoPopOver(channel, imageView);

        // ライブの状態に応じてエフェクトを切り替える
        updateLiveEffect(imageView, channel.isLive());
        channel.liveProperty().addListener((ob, o, n) -> updateLiveEffect(imageView, n));

        return tile;
    }

    private void updateLiveEffect(Node node, boolean live) {
        if (live) {
            node.setEffect(null);
        } else {
            node.setEffect(new SepiaTone());
        }
    }

    public static void installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
        var popup = new LiveInfoPopup(channel);
        popup.setAutoHide(true);

        node.setOnMousePressed(e -> {
            if (channel.isLive() && !popup.isShowing()) {
                var bounds = JavaFXHelper.computeScreenBounds(node);
                var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
                var y = bounds.getMaxY();
                popup.show(node, x, y);
                e.consume();
            }
        });
        node.setOnMouseEntered(e -> {
            if (channel.isLive()) {
                var bounds = JavaFXHelper.computeScreenBounds(node);
                var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
                var y = bounds.getMaxY();
                popup.show(node, x, y);
            }
        });
        node.setOnMouseExited(e -> popup.hide());
    }

    @Override
    protected double computeMinWidth(double height) {
        return USE_COMPUTED_SIZE;
    }

    @Override
    protected double computePrefWidth(double height) {
        return USE_COMPUTED_SIZE;
    }

}
