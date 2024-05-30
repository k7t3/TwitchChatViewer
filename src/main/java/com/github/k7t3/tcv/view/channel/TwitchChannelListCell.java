package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class TwitchChannelListCell extends ListCell<TwitchChannelViewModel> {

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private static final String STYLE_CLASS = "channel-list-cell";
    private static final String PROFILE_IMAGE_STYLE_CLASS = "profile-image-view";
    private static final String NAMES_CONTAINER_CLASS = "names-container";
    private static final String USER_NAME_STYLE_CLASS = "user-name-label";
    private static final String GAME_TITLE_STYLE_CLASS = "game-title-label";
    private static final String ONLINE_MARK_STYLE_CLASS = "online-mark";

    private BorderPane layout;

    private ImageView profileImageView;

    private Label userNameLabel;

    private Label gameTitleLabel;

    private Label viewerCountLabel;

    private Circle onlineMark;

    private BooleanProperty live;

    private Tooltip tooltip;

    public TwitchChannelListCell() {
        getStyleClass().add(STYLE_CLASS);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initialize() {
        profileImageView = new ImageView();
        profileImageView.getStyleClass().add(PROFILE_IMAGE_STYLE_CLASS);
        profileImageView.setFitWidth(PROFILE_IMAGE_WIDTH);
        profileImageView.setFitHeight(PROFILE_IMAGE_HEIGHT);
        var clip = new Rectangle();
        clip.widthProperty().bind(profileImageView.fitWidthProperty());
        clip.heightProperty().bind(profileImageView.fitHeightProperty());
        clip.setArcWidth(PROFILE_IMAGE_WIDTH);
        clip.setArcHeight(PROFILE_IMAGE_HEIGHT);
        profileImageView.setClip(clip);

        live = new SimpleBooleanProperty(false);
        profileImageView.effectProperty().bind(live.map(live -> live ? null : new SepiaTone()));

        userNameLabel = new Label();
        userNameLabel.getStyleClass().add(USER_NAME_STYLE_CLASS);

        gameTitleLabel = new Label();
        gameTitleLabel.getStyleClass().add(GAME_TITLE_STYLE_CLASS);

        onlineMark = new Circle(4);
        onlineMark.getStyleClass().add(ONLINE_MARK_STYLE_CLASS);

        viewerCountLabel = new Label();

        gameTitleLabel.visibleProperty().bind(live);
        gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());
        onlineMark.visibleProperty().bind(live);
        onlineMark.managedProperty().bind(onlineMark.visibleProperty());
        viewerCountLabel.visibleProperty().bind(live);
        viewerCountLabel.managedProperty().bind(viewerCountLabel.visibleProperty());

        // ユーザー名とゲーム名のコンテナ
        var center = new VBox(userNameLabel, gameTitleLabel);
        center.getStyleClass().add(NAMES_CONTAINER_CLASS);
        center.setAlignment(Pos.CENTER_LEFT);
        center.setFillWidth(true);

        // 視聴者数とオンラインアイコン
        var right = new HBox(viewerCountLabel, onlineMark);
        right.setSpacing(10);
        right.setPadding(new Insets(0, 10, 0, 0));
        right.setAlignment(Pos.CENTER_RIGHT);

        layout = new BorderPane();

        center.prefWidthProperty().bind(
                widthProperty()
                        .subtract(200) // FIXME
                        .subtract(right.widthProperty())
                        .subtract(10)
        );

        BorderPane.setAlignment(profileImageView, Pos.CENTER);

        layout.setLeft(profileImageView);
        layout.setCenter(center);
        layout.setRight(right);

        tooltip = new Tooltip();
    }

    private void update(TwitchChannelViewModel viewModel) {
        profileImageView.imageProperty().bind(viewModel.profileImageProperty());

        // ユーザー名はそうそう変更されないことを見越してバインドしない
        var userName = viewModel.getUserName();
        var loginName = viewModel.getUserLogin();
        if (loginName.equalsIgnoreCase(userName)) {
            userNameLabel.setText(userName);
        } else {
            userNameLabel.setText("%s (%s)".formatted(userName, loginName));
        }

        gameTitleLabel.textProperty().bind(viewModel.observableGameName());
        viewerCountLabel.textProperty().bind(
                viewModel.observableViewerCount()
                        .map(count -> count / 1000d)
                        .map(d -> Double.max(d, 0.1))
                        .map("%.1f K"::formatted)
        );
        live.bind(viewModel.liveProperty());

        var title = viewModel.getStreamTitle();
        if (title != null) {
            tooltip.setText(title);
            setTooltip(tooltip);
        } else {
            setTooltip(null);
        }

        // フォローPseudoクラス
        JavaFXHelper.updatePseudoClass(this, "unfollow", !viewModel.isFollowing());
    }

    @Override
    protected void updateItem(TwitchChannelViewModel item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            setTooltip(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        update(item);
        setGraphic(layout);
    }
}
