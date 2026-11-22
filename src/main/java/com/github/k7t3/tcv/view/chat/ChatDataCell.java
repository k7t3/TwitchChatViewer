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
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.view.image.LazyImageView;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.value.ChangeListener;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.ViewResources;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.flowless.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ChatDataCell extends TextFlow implements Cell<ChatDataViewModel, TextFlow> {

    private static final double BADGE_IMAGE_WIDTH = 18;
    private static final double BADGE_IMAGE_HEIGHT = 18;

    private static final double EMOTE_IMAGE_WIDTH = 24;
    private static final double EMOTE_IMAGE_HEIGHT = 24;

    private static final double EMOJI_IMAGE_WIDTH = 20;
    private static final double EMOJI_IMAGE_HEIGHT = 20;

    private static final String STYLE_CLASS = "chat-data-cell";
    private static final String IMAGE_STYLE_VIEW = "chat-image-view";
    private static final String NAME_STYLE_CLASS = "chat-name-label";
    private static final String CHAT_STYLE_CLASS = "chat-text";
    private static final String CHAT_STYLE_SUBS_CLASS = "chat-subs-text";

    private static final String PSEUDO_SUBSCRIBE = "subscribe";
    private static final String PSEUDO_SYSTEM = "system";
    private static final String PSEUDO_CHEER = "cheer";

    private final boolean mergedChat;

    private final BooleanProperty visibleBadges;
    private final BooleanProperty visibleName;
    private final ObservableValue<ChatFont> font;
    private final BooleanProperty deleted;
    private final BooleanProperty hidden;

    private final ChatDataViewModel viewModel;

    private final ViewResources owned = new ViewResources();
    private ViewResources messageResources = new ViewResources();
    private final Function<ChatDataViewModel, ChatDataContextMenu> menus;
    private ChatDataContextMenu contextMenu;

    /** メッセージを構成する、非表示、削除した際に取り除くノード*/
    private final List<Node> deletableNodes = new ArrayList<>();

    public static ChatDataCell of(ChatDataViewModel viewModel) {
        return new ChatDataCell(viewModel, false);
    }

    public static ChatDataCell merged(ChatDataViewModel viewModel) {
        return new ChatDataCell(viewModel, true);
    }

    private ChatDataCell(ChatDataViewModel viewModel, boolean mergedChat) { this(viewModel, mergedChat, ChatDataContextMenu::new); }

    ChatDataCell(ChatDataViewModel viewModel, boolean mergedChat, Function<ChatDataViewModel, ChatDataContextMenu> menus) {
        this.menus = menus;
        this.viewModel = viewModel;
        this.visibleBadges = viewModel.visibleBadgeProperty();
        this.visibleName = viewModel.visibleNameProperty();
        this.deleted = viewModel.deletedProperty();
        this.hidden = viewModel.hiddenProperty();
        this.font = viewModel.fontProperty().when(viewModel.fontProperty().isNotNull());
        this.mergedChat = mergedChat;

        getStyleClass().add(STYLE_CLASS);
        
        if (viewModel.isSystem()) {
            JavaFXHelper.updatePseudoClass(this, PSEUDO_SYSTEM, true);
        }
        
        if (viewModel.isSubs()) {
            JavaFXHelper.updatePseudoClass(this, PSEUDO_SUBSCRIBE, true);
        }

        if (viewModel.isCheered()) {
            JavaFXHelper.updatePseudoClass(this, PSEUDO_CHEER, true);
        }

        owned.add(() -> { setOnContextMenuRequested(null); getChildren().clear(); deletableNodes.clear(); });
        owned.add(this::releaseMenu);
        owned.add(() -> messageResources.close());
        try { initialize(); }
        catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> { if (failure instanceof RuntimeException exception) throw exception; throw (Error) failure; }, this::dispose);
        }
    }

    @Override public void dispose() { owned.close(); }

    private void releaseMenu() {
        var menu = contextMenu; contextMenu = null;
        if (menu == null) return;
        menu.close();
    }

    private <T extends Node> T ownNode(T node, ViewResources resources) {
        resources.add(() -> {
            node.visibleProperty().unbind(); node.managedProperty().unbind();
            if (node instanceof Text text) { text.fontProperty().unbind(); text.fillProperty().unbind(); }
            if (node instanceof LazyImageView image) {
                image.fitWidthProperty().unbind(); image.fitHeightProperty().unbind(); image.lazyImageProperty().unbind(); image.setLazyImage(null);
            }
            if (node instanceof Label label) {
                label.fontProperty().unbind(); label.textProperty().unbind();
                if (label.getTooltip() != null) { label.getTooltip().hide(); label.getTooltip().textProperty().unbind(); label.setTooltip(null); }
                label.setGraphic(null);
            }
        });
        return node;
    }

    @Override
    public TextFlow getNode() {
        return this;
    }

    private void initialize() {

        setPadding(new Insets(4));

        // マージチャットのときはチャンネルのイメージを追加
        if (mergedChat) {
            var channelIcon = createBadgeNode(viewModel.getChannel().getProfileImage());
            channelIcon.managedProperty().unbind();
            channelIcon.visibleProperty().unbind();
            getChildren().add(channelIcon);
        }

        // システムメッセージ
        if (viewModel.isSystem()) {
            initForSystemMessage();
            return;
        }

        // サブスクライブメッセージ
        if (viewModel.isSubs()) {

            var isEmptyMessage = viewModel.getChatData().message().getPlain().isEmpty();

            var format = Resources.getResourceBundle().getString("chat.subs.format");
            var message = format.formatted(viewModel.getDisplayName());
            if (!isEmptyMessage) {
                message += "\n";
            }

            var subsText = ownNode(new Text(message), owned);
            subsText.fontProperty().bind(font.map(ChatFont::getBoldFont));
            subsText.getStyleClass().add(CHAT_STYLE_SUBS_CLASS);
            getChildren().add(subsText);

            // サブスクライブメッセージが空のときは終わり
            if (isEmptyMessage) {
                return;
            }
        }

        // チアーメッセージ
        if (viewModel.isCheered()) {

            var isEmptyMessage = viewModel.getChatData().message().getPlain().isEmpty();

            var format = Resources.getResourceBundle().getString("chat.cheer.format");
            var message = format.formatted(viewModel.getDisplayName(), viewModel.getBits());
            if (!isEmptyMessage) {
                message += "\n";
            }

            var subsText = ownNode(new Text(message), owned);
            subsText.fontProperty().bind(font.map(ChatFont::getBoldFont));
            subsText.getStyleClass().add(CHAT_STYLE_SUBS_CLASS);
            getChildren().add(subsText);

            // メッセージが空のときは終わり
            if (isEmptyMessage) {
                return;
            }
        }


        viewModel.getBadges().stream().map(this::createBadgeNode).forEach(getChildren()::add);

        var userNameText = ownNode(new Text(), owned);
        userNameText.getStyleClass().addAll(NAME_STYLE_CLASS, Styles.TEXT_BOLDER);
        userNameText.visibleProperty().bind(visibleName);
        userNameText.managedProperty().bind(visibleName);
        userNameText.fillProperty().bind(viewModel.colorProperty());
        userNameText.fontProperty().bind(font.map(ChatFont::getFont));

        var colon = ownNode(new Text(": "), owned);
        colon.getStyleClass().add(NAME_STYLE_CLASS);
        colon.visibleProperty().bind(visibleName);
        colon.managedProperty().bind(visibleName);
        colon.fillProperty().bind(viewModel.colorProperty());
        colon.fontProperty().bind(font.map(ChatFont::getFont));

        // ユーザー名と表示名が異なるとき
        if (!viewModel.getDisplayName().equalsIgnoreCase(viewModel.getUserName())) {
            userNameText.setText(" %s(%s)".formatted(viewModel.getDisplayName(), viewModel.getUserName()));
        } else {
            userNameText.setText(" %s".formatted(viewModel.getUserName()));
        }

        getChildren().addAll(userNameText, colon);

        // 削除済みのメッセージ
        if (deleted.get()) {
            disabledMessage(Resources.getString("chat.message.deleted"));
            return;
        }

        // 非表示にしたメッセージ
        if (hidden.get()) {
            disabledMessage(Resources.getString("chat.message.hidden"));
            return;
        }

        // コンテキストメニュー
        setOnContextMenuRequested(e -> {
            if (owned.isClosed()) return;
            if (contextMenu == null) {
                contextMenu = menus.apply(viewModel);
            }
            if (owned.isClosed() || deleted.get() || hidden.get()) { releaseMenu(); return; }

            if (contextMenu.isShowing())
                contextMenu.hide();
            else
                contextMenu.show(this, e.getScreenX(), e.getScreenY());
        });


        // 削除フラグが有効なときメッセージ要素を
        // すべてクリアして代替メッセージを表示する
        ChangeListener<Boolean> deletion = (ob, oldValue, deleted) -> {
            if (deleted && !owned.isClosed()) disabledMessage(Resources.getString("chat.message.deleted"));
        };
        ChangeListener<Boolean> filtering = (ob, oldValue, hidden) -> {
            if (hidden && !owned.isClosed()) disabledMessage(Resources.getString("chat.message.hidden"));
        };
        this.deleted.addListener(deletion); owned.add(() -> this.deleted.removeListener(deletion));
        this.hidden.addListener(filtering); owned.add(() -> this.hidden.removeListener(filtering));

        buildMessageNodes();
    }

    private void buildMessageNodes() {
        deletableNodes.clear();

        for (var fragment : viewModel.getMessage()) {
            switch (fragment.type()) {
                case EMOTE -> {
                    var view = createEmoteNode(fragment);
                    deletableNodes.add(view);
                    getChildren().add(view);
                }
                case EMOJI -> {
                    var view = createEmojiNode(fragment);
                    deletableNodes.add(view);
                    getChildren().add(view);
                }
                case TEXT -> {
                    var view = createTextNode(fragment);
                    deletableNodes.add(view);
                    getChildren().add(view);
                }
            }
        }
    }

    private void initForSystemMessage() {
        var spacer = new Pane();
        spacer.setPrefWidth(4);
        spacer.setPrefHeight(USE_COMPUTED_SIZE);

        getChildren().addAll(spacer);

        buildMessageNodes();
    }

    private void disabledMessage(String message) {
        if (owned.isClosed()) return;
        setOnContextMenuRequested(null);
        CloseActions.runAll(this::releaseMenu, () -> replaceMessage(message));
    }

    private void replaceMessage(String message) {
        if (owned.isClosed()) return;
        getChildren().removeAll(deletableNodes); deletableNodes.clear();
        var previous = messageResources;
        messageResources = new ViewResources();
        CloseActions.runAll(previous::close, () -> {
            if (owned.isClosed()) return;
            var text = ownNode(new Text(deleted.get() ? Resources.getString("chat.message.deleted") : message), messageResources);
            text.getStyleClass().addAll(CHAT_STYLE_CLASS, Styles.TEXT_MUTED);
            text.fontProperty().bind(font.map(ChatFont::getFont));
            deletableNodes.add(text); getChildren().add(text);
        });
    }

    private Node createTextNode(ChatMessageFragment fragment) {
        var text = ownNode(new Text(fragment.text()), messageResources);
        text.getStyleClass().add(CHAT_STYLE_CLASS);
        text.fontProperty().bind(font.map(f ->
                viewModel.isSystem() ? f.getBoldFont() : f.getFont())
        );
        return text;
    }

    private Label createImageNode(LazyImage image, double width, double height, ViewResources resources) {
        var imageView = ownNode(new LazyImageView(image), resources);
        imageView.getStyleClass().add(IMAGE_STYLE_VIEW);
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(font.map(current -> current.getFontScale() * width));
        imageView.fitHeightProperty().bind(font.map(current -> current.getFontScale() * height));
        var node = ownNode(new Label(null, imageView), resources);
        node.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return node;
    }

    private Node createBadgeNode(LazyImage image) {
        var node = createImageNode(image, BADGE_IMAGE_WIDTH, BADGE_IMAGE_HEIGHT, owned);
        node.setPadding(new Insets(0, 1, 0, 1));
        node.visibleProperty().bind(visibleBadges);
        node.managedProperty().bind(visibleBadges);
        return node;
    }

    private Node createEmoteNode(ChatMessageFragment fragment) {
        var node = createImageNode(viewModel.getEmoteStore().get(fragment.text()), EMOTE_IMAGE_WIDTH, EMOTE_IMAGE_HEIGHT, messageResources);
        node.setTooltip(new Tooltip(fragment.additional()));
        return node;
    }

    private Node createEmojiNode(ChatMessageFragment fragment) {
        var image = viewModel.getEmojiImage(fragment);
        return image == null ? createTextNode(fragment) : createImageNode(image, EMOJI_IMAGE_WIDTH, EMOJI_IMAGE_HEIGHT, messageResources);
    }

    @Override
    protected double computeMinWidth(double height) {
        return Label.USE_COMPUTED_SIZE;
    }

    @Override
    protected double computePrefWidth(double height) {
        return Label.USE_COMPUTED_SIZE;
    }

    @Override
    protected double computePrefHeight(double width) {
        return super.computePrefHeight(width);
    }

}
