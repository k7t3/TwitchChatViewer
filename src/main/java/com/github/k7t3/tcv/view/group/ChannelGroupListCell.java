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
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import com.github.k7t3.tcv.lifecycle.CloseActions;

public class ChannelGroupListCell extends Card implements Cell<ChannelGroup, Region> {

    private static final String STYLE_CLASS = "channel-group-cell";
    private static final String TILE_STYLE_CLASS = "channel-group-tile";

    private static final double PROFILE_IMAGE_WIDTH = 48;
    private static final double PROFILE_IMAGE_HEIGHT = 48;

    private final EditableLabel title = new EditableLabel();
    private final ToggleButton pin = new ToggleButton(null, new FontIcon());
    private final EditableLabel comment = new EditableLabel();
    private final TilePane tilePane = new TilePane();

    private boolean disposed;
    private Alert confirmation;
    private final List<Runnable> tiles = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final GeneralPreferences generalPrefs;
    private final ChannelGroup group;
    private final ChannelGroupListViewModel viewModel;
    private final Runnable showTop;
    private final ChangeListener<Boolean> pinnedChanged = (observable, old, value) -> { if (!disposed) pin.setSelected(value); };
    private final ChangeListener<String> nameChanged = (observable, old, value) -> { if (!disposed) title.setText(value); };
    private final ChangeListener<String> commentChanged = (observable, old, value) -> { if (!disposed) comment.setText(value); };

    public ChannelGroupListCell(
            GeneralPreferences generalPrefs,
            ChannelGroup group,
            ChannelGroupListViewModel viewModel,
            Runnable showTop
    ) {
        this.generalPrefs = generalPrefs;
        this.group = group;
        this.viewModel = viewModel;
        this.showTop = showTop;
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
        pin.setAccessibleText(Resources.getString("group.pin.tooltip"));

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
            if (disposed) return;

            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(getScene().getWindow());
            alert.setTitle(Resources.getString("group.dialog.delete.title"));
            alert.setHeaderText(Resources.getString("group.dialog.delete.header"));
            alert.setContentText(Resources.getString("group.dialog.delete.content").formatted(group.getName()));
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            confirmation = alert;
            java.util.Optional<ButtonType> result;
            try { result = alert.showAndWait(); }
            finally { if (confirmation == alert) confirmation = null; }
            if (disposed || result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }

            setDisable(true);
            var t = viewModel.delete(group);
            t.setFinally(() -> { if (!disposed) setDisable(false); });
        });

        var footer = new HBox(openChatButton, deleteButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setSpacing(10);
        footer.setPadding(new Insets(0d, 4d, 4d, 2d));
        setFooter(footer);

        actions.add(() -> {
            openSeparatedItem.setOnAction(null); openMergedItem.setOnAction(null);
            openChatButton.setOnAction(null); openChatButton.hide(); deleteButton.setOnAction(null);
        });
        pin.setOnAction(e -> {
            setDisable(true);
            group.setPinned(pin.isSelected());
            var t = viewModel.update(group);
            t.setFinally(() -> { if (!disposed) setDisable(false); });
            t.onDone(showTop);
        });
        title.setOnEditCommit(e -> {
            setDisable(true);
            group.setName(e.getNewValue());
            var t = viewModel.update(group);
            t.setFinally(() -> { if (!disposed) setDisable(false); });
        });
        comment.setOnEditCommit(e -> {
            setDisable(true);
            group.setComment(e.getNewValue());
            var t = viewModel.update(group);
            t.setFinally(() -> { if (!disposed) setDisable(false); });
        });
    }

    @Override
    public Region getNode() {
        return this;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        CloseActions.runAll(() -> {
            var dialog = confirmation; confirmation = null;
            if (dialog != null) { dialog.setResult(ButtonType.NO); dialog.close(); }
        }, () -> {
            group.nameProperty().removeListener(nameChanged);
            group.commentProperty().removeListener(commentChanged);
            group.pinnedProperty().removeListener(pinnedChanged);
            title.setOnEditCommit(null); comment.setOnEditCommit(null); pin.setOnAction(null);
        }, () -> release(actions), () -> release(tiles), () -> tilePane.getChildren().clear());
    }

    private static void release(List<Runnable> actions) {
        var owned = actions.toArray(Runnable[]::new); actions.clear(); CloseActions.runAll(owned);
    }

    public void update() {
        if (disposed) return;
        release(tiles);
        group.nameProperty().removeListener(nameChanged); group.commentProperty().removeListener(commentChanged);
        group.pinnedProperty().removeListener(pinnedChanged);
        title.setText(group.getName()); comment.setText(group.getComment()); pin.setSelected(group.isPinned());
        group.nameProperty().addListener(nameChanged); group.commentProperty().addListener(commentChanged);
        group.pinnedProperty().addListener(pinnedChanged);
        tilePane.getChildren().clear();
        tilePane.setHgap(4);
        tilePane.setVgap(2);

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

        // チャンネルページをブラウザで開く
        var openPageMenuItem = new OpenBrowserMenuItem(channel);
        tiles.add(openPageMenuItem::close);

        // グループから削除
        var removeMenuItem = new MenuItem(Resources.getString("group.button.remove"), new FontIcon(Feather.X));
        removeMenuItem.setOnAction(e -> {
            if (disposed) return;
            group.getChannels().remove(channel);
            setDisable(true);
            var t = viewModel.update(group);
            t.onDone(() -> { if (!disposed) tilePane.getChildren().remove(tile); });
            t.setFinally(() -> { if (!disposed) setDisable(false); });
        });

        // チャットを開く
        var openChatMenuItem = new OpenChatMenuItem(channel);
        tiles.add(openChatMenuItem::close);

        // タイルのアクション(MenuButton)
        var menuButton = new MenuButton(null, new FontIcon(Feather.MORE_VERTICAL));
        menuButton.setAccessibleText(Resources.getString("group.channel.menu"));
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
        tiles.add(installStreamInfoPopOver(channel, imageView));

        // ライブの状態に応じてエフェクトを切り替える
        updateLiveEffect(imageView, channel.isLive());
        ChangeListener<Boolean> liveChanged = (observable, old, value) -> { if (!disposed) updateLiveEffect(imageView, value); };
        channel.liveProperty().addListener(liveChanged);
        tiles.add(() -> {
            channel.liveProperty().removeListener(liveChanged);
            tile.titleProperty().unbind(); tile.descriptionProperty().unbind();
            imageView.lazyImageProperty().unbind(); imageView.setLazyImage(null); imageView.setEffect(null);
            clip.widthProperty().unbind(); clip.heightProperty().unbind();
            clip.arcWidthProperty().unbind(); clip.arcHeightProperty().unbind(); imageView.setClip(null);
            menuButton.hide(); menuButton.getItems().forEach(item -> item.setOnAction(null)); menuButton.getItems().clear();
        });

        return tile;
    }

    private void updateLiveEffect(Node node, boolean live) {
        if (live) {
            node.setEffect(null);
        } else {
            node.setEffect(new SepiaTone());
        }
    }

    public static Runnable installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
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
        return () -> {
            node.setOnMousePressed(null); node.setOnMouseEntered(null); node.setOnMouseExited(null);
            popup.close();
        };
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
