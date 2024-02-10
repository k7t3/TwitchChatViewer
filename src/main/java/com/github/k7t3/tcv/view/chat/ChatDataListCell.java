package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.vm.chat.ChannelEmoteStore;
import com.github.k7t3.tcv.vm.chat.ChatDataViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

public class ChatDataListCell extends ListCell<ChatDataViewModel> {

    private static final double BADGE_IMAGE_WIDTH = 18;
    private static final double BADGE_IMAGE_HEIGHT = 18;

    private static final double EMOTE_IMAGE_WIDTH = 22;
    private static final double EMOTE_IMAGE_HEIGHT = 22;

    private static final String STYLE_CLASS = "chat-data-view";
    private static final String IMAGE_STYLE_VIEW = "chat-image-view";
    private static final String NAME_STYLE_CLASS = "chat-name-label";
    private static final String CHAT_STYLE_CLASS = "chat-text";

    private final BooleanProperty visibleBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleName = new SimpleBooleanProperty(true);

    private final BooleanProperty differentName = new SimpleBooleanProperty(false);

    private BorderPane layout;

    private HBox badgesNode;

    private List<Node> messages;

    private TextFlow textFlow;

    private ChannelEmoteStore emoteStore;

    private Text bracketLeft;

    private Text userNameLabel;

    private Text bracketRight;

    private Text loginLabel;

    public ChatDataListCell() {
        getStyleClass().add(STYLE_CLASS);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    public void initialize() {

        layout = new BorderPane();

        badgesNode = new HBox(4);
        badgesNode.setAlignment(Pos.TOP_LEFT);
        badgesNode.visibleProperty().bind(visibleBadges);
        badgesNode.managedProperty().bind(visibleBadges);

        layout.setLeft(badgesNode);

        userNameLabel = new Text();
        userNameLabel.getStyleClass().add(NAME_STYLE_CLASS);
        userNameLabel.visibleProperty().bind(visibleName);
        userNameLabel.managedProperty().bind(visibleName);

        var difference = visibleName.and(differentName);

        bracketLeft = new Text(" (");
        bracketLeft.getStyleClass().add(NAME_STYLE_CLASS);
        bracketLeft.visibleProperty().bind(difference);
        bracketLeft.managedProperty().bind(difference);

        loginLabel = new Text();
        loginLabel.getStyleClass().add(NAME_STYLE_CLASS);
        loginLabel.visibleProperty().bind(difference);
        loginLabel.managedProperty().bind(difference);

        bracketRight = new Text(")");
        bracketRight.getStyleClass().add(NAME_STYLE_CLASS);
        bracketRight.visibleProperty().bind(difference);
        bracketRight.managedProperty().bind(difference);

        var semicolon = new Text(": ");
        semicolon.visibleProperty().bind(visibleName);
        semicolon.managedProperty().bind(visibleName);

        messages = new ArrayList<>();

        textFlow = new TextFlow(
                userNameLabel,
                bracketLeft,
                loginLabel,
                bracketRight,
                semicolon
        );

        textFlow.maxWidthProperty().bind(
                widthProperty().subtract(badgesNode.widthProperty().add(10))
        );

        BorderPane.setAlignment(textFlow, Pos.CENTER_LEFT);
        layout.setCenter(textFlow);

    }

    private void buildMessage(ChatMessage message) {
        textFlow.getChildren().removeAll(messages);
        messages.clear();

        for (var fragment : message) {
            switch (fragment.type()) {
                case EMOTE -> {
                    var view = createEmoteImageView(emoteStore.get(fragment.fragment()));
                    messages.add(view);
                }
                case MESSAGE -> {
                    var text = new Text(fragment.fragment());
                    text.getStyleClass().add(CHAT_STYLE_CLASS);
                    messages.add(text);
                }
            }
        }

        textFlow.getChildren().addAll(messages);
    }

    private ImageView createBadgeImageView(Image image) {
        var view = new ImageView(image);
        view.getStyleClass().add(IMAGE_STYLE_VIEW);
        view.setFitWidth(BADGE_IMAGE_WIDTH);
        view.setFitHeight(BADGE_IMAGE_HEIGHT);
        return view;
    }

    private ImageView createEmoteImageView(Image image) {
        var view = new ImageView(image);
        view.getStyleClass().add(IMAGE_STYLE_VIEW);
        view.setFitWidth(EMOTE_IMAGE_WIDTH);
        view.setFitHeight(EMOTE_IMAGE_HEIGHT);
        return view;
    }

    private void updateNodes(ChatDataViewModel viewModel) {

        // バッジ
        var badges = viewModel.getBadges()
                .stream()
                .map(this::createBadgeImageView)
                .toList();
        badgesNode.getChildren().setAll(badges);

        userNameLabel.setText(viewModel.getDisplayName());
        userNameLabel.setFill(viewModel.getColor());

        differentName.set(!viewModel.getDisplayName().equalsIgnoreCase(viewModel.getUserName()));

        bracketLeft.setFill(viewModel.getColor());
        loginLabel.setText(viewModel.getUserName());
        loginLabel.setFill(viewModel.getColor());
        bracketRight.setFill(viewModel.getColor());

        if (emoteStore == null) {
            emoteStore = viewModel.getEmoteStore();
        }

        buildMessage(viewModel.getMessage());

    }

    @Override
    protected void updateItem(ChatDataViewModel item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            if (layout != null) {
                badgesNode.getChildren().clear();
                textFlow.getChildren().removeAll(messages);
                messages.clear();
            }
            setGraphic(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        updateNodes(item);
        setGraphic(layout);
    }

    public BooleanProperty visibleBadgesProperty() { return visibleBadges; }
    public boolean isVisibleBadges() { return visibleBadges.get(); }
    public void setVisibleBadges(boolean visibleBadges) { this.visibleBadges.set(visibleBadges); }

    public BooleanProperty visibleNameProperty() { return visibleName; }
    public boolean isVisibleName() { return visibleName.get(); }
    public void setVisibleName(boolean visibleName) { this.visibleName.set(visibleName); }
}
