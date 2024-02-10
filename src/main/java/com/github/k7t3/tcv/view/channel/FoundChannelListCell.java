package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.vm.channel.FoundChannelViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class FoundChannelListCell extends ListCell<FoundChannelViewModel> {

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private static final String STYLE_CLASS = "follow-channel-view";
    private static final String PROFILE_IMAGE_STYLE_CLASS = "profile-image-view";
    private static final String NAMES_CONTAINER_CLASS = "names-container";
    private static final String USER_NAME_STYLE_CLASS = "user-name-label";
    private static final String GAME_TITLE_STYLE_CLASS = "game-title-label";

    private BorderPane layout;

    private ImageView profileImageView;

    private Label userNameLabel;

    private Label gameTitleLabel;

    private BooleanProperty live;

    public FoundChannelListCell() {
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

        gameTitleLabel.visibleProperty().bind(live);
        gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());

        // ユーザー名とゲーム名のコンテナ
        var center = new VBox(userNameLabel, gameTitleLabel);
        center.getStyleClass().add(NAMES_CONTAINER_CLASS);
        center.setAlignment(Pos.CENTER_LEFT);
        center.setFillWidth(true);
        center.setPadding(new Insets(0, 0, 0, 4));

        layout = new BorderPane();

        center.prefWidthProperty().bind(
                widthProperty().subtract(100)
        );

        BorderPane.setAlignment(profileImageView, Pos.CENTER);

        layout.setLeft(profileImageView);
        layout.setCenter(center);
    }

    private void update(FoundChannelViewModel viewModel) {
        profileImageView.imageProperty().bind(viewModel.profileImageProperty());

        // ユーザー名はそうそう変更されないことを見越してバインドしない
        var userName = viewModel.getBroadcaster().getUserName();
        var loginName = viewModel.getBroadcaster().getUserLogin();
        if (loginName.equalsIgnoreCase(userName)) {
            userNameLabel.setText(userName);
        } else {
            userNameLabel.setText("%s (%s)".formatted(userName, loginName));
        }

        gameTitleLabel.setText(viewModel.getGameName());
        live.set(viewModel.isLive());
    }

    @Override
    protected void updateItem(FoundChannelViewModel item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        update(item);
        setGraphic(layout);
    }
}
