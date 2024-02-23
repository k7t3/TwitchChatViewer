package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.fxmisc.flowless.Cell;

public class ChatDataCell extends TextFlow implements Cell<ChatDataViewModel, TextFlow> {

    private static final double BADGE_IMAGE_WIDTH = 18;
    private static final double BADGE_IMAGE_HEIGHT = 18;

    private static final double EMOTE_IMAGE_WIDTH = 24;
    private static final double EMOTE_IMAGE_HEIGHT = 24;

    private static final String STYLE_CLASS = "chat-data-view";
    private static final String IMAGE_STYLE_VIEW = "chat-image-view";
    private static final String NAME_STYLE_CLASS = "chat-name-label";
    private static final String CHAT_STYLE_CLASS = "chat-text";

    private final BooleanProperty visibleBadges;

    private final BooleanProperty visibleName;

    private final ObservableValue<Font> font;

    private final ChatDataViewModel viewModel;

    private ContextMenu contextMenu;

    public ChatDataCell(ChatDataViewModel viewModel) {
        this.viewModel = viewModel;
        this.visibleBadges = viewModel.visibleBadgeProperty();
        this.visibleName = viewModel.visibleNameProperty();
        this.font = viewModel.fontProperty().when(viewModel.fontProperty().isNotNull());

        getStyleClass().add(STYLE_CLASS);

        initialize();
    }

    @Override
    public TextFlow getNode() {
        return this;
    }

    public void initialize() {

        setPadding(new Insets(4));

        viewModel.getBadges().stream().map(this::createBadgeNode).forEach(getChildren()::add);

        var userNameText = new Text();
        userNameText.getStyleClass().add(NAME_STYLE_CLASS);
        userNameText.visibleProperty().bind(visibleName);
        userNameText.managedProperty().bind(visibleName);
        userNameText.fillProperty().bind(viewModel.colorProperty());
        userNameText.fontProperty().bind(font);

        var colon = new Text(": ");
        colon.getStyleClass().add(NAME_STYLE_CLASS);
        colon.visibleProperty().bind(visibleName);
        colon.managedProperty().bind(visibleName);
        colon.fontProperty().bind(font);

        // ユーザー名と表示名が異なるとき
        if (!viewModel.getDisplayName().equalsIgnoreCase(viewModel.getUserName())) {
            userNameText.setText(" %s(%s)".formatted(viewModel.getDisplayName(), viewModel.getUserName()));
        } else {
            userNameText.setText(" %s".formatted(viewModel.getUserName()));
        }

        getChildren().addAll(userNameText, colon);

        for (var fragment : viewModel.getMessage()) {
            switch (fragment.type()) {
                case EMOTE -> {
                    var view = createEmoteNode(viewModel.getEmoteStore().get(fragment.fragment()));
                    getChildren().add(view);
                }
                case MESSAGE -> {
                    var text = new Text(fragment.fragment());
                    text.getStyleClass().add(CHAT_STYLE_CLASS);
                    text.fontProperty().bind(font);
                    getChildren().add(text);
                }
            }
        }

        setOnContextMenuRequested(e -> {
            if (contextMenu == null) {
                contextMenu = new ChatDataContextMenu(viewModel);
            }

            if (contextMenu.isShowing())
                contextMenu.hide();
            else
                contextMenu.show(this, e.getScreenX(), e.getScreenY());
        });

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
