package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.prefs.ChatFont;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.flowless.Cell;

import java.util.ArrayList;
import java.util.List;

public class ChatDataCell extends TextFlow implements Cell<ChatDataViewModel, TextFlow> {

    private static final double BADGE_IMAGE_WIDTH = 18;
    private static final double BADGE_IMAGE_HEIGHT = 18;

    private static final double EMOTE_IMAGE_WIDTH = 24;
    private static final double EMOTE_IMAGE_HEIGHT = 24;

    private static final String STYLE_CLASS = "chat-data-cell";
    private static final String IMAGE_STYLE_VIEW = "chat-image-view";
    private static final String NAME_STYLE_CLASS = "chat-name-label";
    private static final String CHAT_STYLE_CLASS = "chat-text";
    private static final String CHAT_STYLE_SUBS_CLASS = "chat-subs-text";

    private static final String PSEUDO_SUBSCRIBE = "subscribe";
    private static final String PSEUDO_SYSTEM = "system";

    private final boolean mergedChat;

    private final BooleanProperty visibleBadges;

    private final BooleanProperty visibleName;

    private final ObservableValue<ChatFont> font;

    private final BooleanProperty deleted;

    private final BooleanProperty hidden;

    private final ChatDataViewModel viewModel;

    private ContextMenu contextMenu;

    public static ChatDataCell of(ChatDataViewModel viewModel) {
        return new ChatDataCell(viewModel, false);
    }

    public static ChatDataCell merged(ChatDataViewModel viewModel) {
        return new ChatDataCell(viewModel, true);
    }

    private ChatDataCell(ChatDataViewModel viewModel, boolean mergedChat) {
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

        initialize();
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

        // 削除済みのメッセージ
        if (deleted.get()) {
            disabledMessage("chat.message.deleted");
            return;
        }

        // 非表示にしたメッセージ
        if (hidden.get()) {
            disabledMessage("chat.message.hidden");
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

            var subsText = new Text(message);
            subsText.fontProperty().bind(font.map(ChatFont::getBoldFont));
            subsText.getStyleClass().add(CHAT_STYLE_SUBS_CLASS);
            getChildren().add(subsText);

            // サブスクライブメッセージが空のときは終わり
            if (isEmptyMessage) {
                return;
            }
        }


        viewModel.getBadges().stream().map(this::createBadgeNode).forEach(getChildren()::add);

        var userNameText = new Text();
        userNameText.getStyleClass().addAll(NAME_STYLE_CLASS, Styles.TEXT_BOLDER);
        userNameText.visibleProperty().bind(visibleName);
        userNameText.managedProperty().bind(visibleName);
        userNameText.fillProperty().bind(viewModel.colorProperty());
        userNameText.fontProperty().bind(font.map(ChatFont::getFont));

        var colon = new Text(": ");
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


        // コンテキストメニュー
        setOnContextMenuRequested(e -> {
            if (contextMenu == null) {
                contextMenu = new ChatDataContextMenu(viewModel);
            }

            if (contextMenu.isShowing())
                contextMenu.hide();
            else
                contextMenu.show(this, e.getScreenX(), e.getScreenY());
        });


        // メッセージを構成するノードリスト
        var messageNodes = new ArrayList<Node>();
        // 削除フラグが有効なときメッセージ要素を
        // すべてクリアして代替メッセージを表示する
        deleted.addListener((ob, o, n) -> {
            if (n) {
                disabledMessage(messageNodes, Resources.getString("chat.message.deleted"));
                messageNodes.clear();
            }
        });
        hidden.addListener((ob, o, n) -> {
            if (n) {
                disabledMessage(messageNodes, Resources.getString("chat.message.hidden"));
                messageNodes.clear();
            }
        });

        for (var fragment : viewModel.getMessage()) {
            switch (fragment.type()) {
                case EMOTE -> {
                    var view = createEmoteNode(viewModel.getEmoteStore().get(fragment.fragment()));
                    messageNodes.add(view);
                    getChildren().add(view);
                }
                case MESSAGE -> {
                    var text = new Text(fragment.fragment());
                    text.getStyleClass().add(CHAT_STYLE_CLASS);
                    text.fontProperty().bind(font.map(f ->
                            viewModel.isSystem() ? f.getBoldFont() : f.getFont())
                    );
                    messageNodes.add(text);
                    getChildren().add(text);
                }
            }
        }

    }

    private void initForSystemMessage() {
        var spacer = new Pane();
        spacer.setPrefWidth(4);
        spacer.setPrefHeight(USE_COMPUTED_SIZE);

        getChildren().addAll(spacer);

        for (var fragment : viewModel.getMessage()) {
            switch (fragment.type()) {
                case EMOTE -> {
                    var view = createEmoteNode(viewModel.getEmoteStore().get(fragment.fragment()));
                    getChildren().add(view);
                }
                case MESSAGE -> {
                    var text = new Text(fragment.fragment());
                    text.getStyleClass().add(CHAT_STYLE_CLASS);
                    text.fontProperty().bind(font.map(ChatFont::getBoldFont));
                    getChildren().add(text);
                }
            }
        }
    }

    private void disabledMessage(String localizedKey) {
        var message = Resources.getString(localizedKey);
        disabledMessage(List.of(), message);
    }

    private void disabledMessage(List<Node> messageNodes, String alternateMessage) {
        if (!messageNodes.isEmpty()) {
            // メッセージを構成するノードを除去
            getChildren().removeAll(messageNodes);
        }

        // 代替メッセージ
        var text = new Text(alternateMessage);
        text.getStyleClass().addAll(CHAT_STYLE_CLASS, Styles.TEXT_MUTED);
        text.fontProperty().bind(font.map(ChatFont::getFont));
        getChildren().add(text);
    }

    private Node createBadgeNode(Image image) {
        var imageView = new ImageView(image);
        imageView.getStyleClass().add(IMAGE_STYLE_VIEW);
        imageView.setFitWidth(BADGE_IMAGE_WIDTH);
        imageView.setFitHeight(BADGE_IMAGE_HEIGHT);
        imageView.setPreserveRatio(true);

        var node = new Label(null, imageView);
        node.setPadding(new Insets(0, 1, 0, 1));
        node.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        node.visibleProperty().bind(visibleBadges);
        node.managedProperty().bind(visibleBadges);
        return node;
    }

    private Node createEmoteNode(Image image) {
        var imageView = new ImageView(image);
        imageView.getStyleClass().add(IMAGE_STYLE_VIEW);
        imageView.setFitWidth(EMOTE_IMAGE_WIDTH);
        imageView.setFitHeight(EMOTE_IMAGE_HEIGHT);
        imageView.setPreserveRatio(true);

        var node = new Label(null, imageView);
        node.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return node;
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
