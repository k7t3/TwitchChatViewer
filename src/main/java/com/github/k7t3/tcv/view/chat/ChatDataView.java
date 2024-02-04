package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.vm.chat.ChannelEmoteStore;
import com.github.k7t3.tcv.vm.chat.ChatDataViewModel;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.JavaView;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ChatDataView extends TextFlow implements JavaView<ChatDataViewModel>, Initializable {

    private static final double BADGE_IMAGE_WIDTH = 18;
    private static final double BADGE_IMAGE_HEIGHT = 18;

    private static final double EMOTE_IMAGE_WIDTH = 28;
    private static final double EMOTE_IMAGE_HEIGHT = 28;

    private static final String STYLE_CLASS = "chat-data-view";
    private static final String IMAGE_STYLE_VIEW = "chat-image-view";
    private static final String NAME_STYLE_CLASS = "chat-name-label";
    private static final String CHAT_STYLE_CLASS = "chat-text";

    private ObservableList<Node> badges;

    private List<Node> messages;

    private ChannelEmoteStore emoteStore;

    private Text userNameLabel;

    private Text loginLabel;

    @InjectViewModel
    private ChatDataViewModel viewModel;

    public ChatDataView() {
        getStyleClass().add(STYLE_CLASS);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        badges = FXCollections.observableArrayList();
        var badgesNode = new HBox(4);
        badgesNode.setAlignment(Pos.CENTER_LEFT);
        Bindings.bindContent(badgesNode.getChildren(), badges);

        // バッジの変更を監視する
        viewModel.getBadges().addListener((ListChangeListener<? super Image>) (c) -> {
            while (c.next()) {
                if (c.wasRemoved()) badges.clear();
                if (c.wasAdded()) {
                    for (var image : c.getAddedSubList())
                        badges.add(createBadgeImageView(image));
                }
            }
        });
        // 初期化
        viewModel.getBadges().stream().map(this::createBadgeImageView).forEach(badges::add);

        var colorBind = viewModel.colorProperty();
        var difference = viewModel.displayNameProperty().isEqualToIgnoreCase(viewModel.userNameProperty()).not();

        userNameLabel = new Text();
        userNameLabel.getStyleClass().add(NAME_STYLE_CLASS);
        userNameLabel.textProperty().bind(viewModel.displayNameProperty());
        userNameLabel.fillProperty().bind(colorBind);

        var bracketLeft = new Text(" (");
        bracketLeft.getStyleClass().add(NAME_STYLE_CLASS);
        bracketLeft.fillProperty().bind(colorBind);
        bracketLeft.visibleProperty().bind(difference);
        bracketLeft.managedProperty().bind(difference);

        loginLabel = new Text();
        loginLabel.getStyleClass().add(NAME_STYLE_CLASS);
        loginLabel.textProperty().bind(viewModel.userNameProperty());
        loginLabel.fillProperty().bind(colorBind);
        loginLabel.visibleProperty().bind(difference);
        loginLabel.managedProperty().bind(difference);

        var bracketRight = new Text(")");
        bracketRight.getStyleClass().add(NAME_STYLE_CLASS);
        bracketRight.fillProperty().bind(colorBind);
        bracketRight.visibleProperty().bind(difference);
        bracketRight.managedProperty().bind(difference);

        var semicolon = new Text(": ");

        messages = new ArrayList<>();
        emoteStore = viewModel.getEmoteStore();

        // メッセージの変更を監視する
        viewModel.messageProperty().addListener((ob, o, n) -> buildMessage(n));

        getChildren().addAll(
                badgesNode,
                userNameLabel,
                bracketLeft,
                loginLabel,
                bracketRight,
                semicolon
        );

        // メッセージを初期化
        buildMessage(viewModel.getMessage());
    }

    private void buildMessage(ChatMessage message) {
        getChildren().removeAll(messages);
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

        getChildren().addAll(messages);
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

}
