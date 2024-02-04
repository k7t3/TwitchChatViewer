package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.vm.channel.FollowChannelViewModel;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.JavaView;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.ResourceBundle;

public class FollowChannelView extends BorderPane implements JavaView<FollowChannelViewModel>, Initializable {

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private static final String STYLE_CLASS = "follow-channel-view";
    private static final String PROFILE_IMAGE_STYLE_CLASS = "profile-image-view";
    private static final String NAMES_CONTAINER_CLASS = "names-container";
    private static final String USER_NAME_STYLE_CLASS = "user-name-label";
    private static final String GAME_TITLE_STYLE_CLASS = "game-title-label";
    private static final String ONLINE_MARK_STYLE_CLASS = "online-mark";

    private ImageView profileImageView;

    private Label userNameLabel;

    private Label loginLabel;

    private Label gameTitleLabel;

    private Label viewerCountLabel;

    private Circle onlineMark;

    @InjectViewModel
    private FollowChannelViewModel viewModel;

    public FollowChannelView() {
        getStyleClass().add(STYLE_CLASS);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        profileImageView = new ImageView();
        profileImageView.getStyleClass().add(PROFILE_IMAGE_STYLE_CLASS);
        profileImageView.setFitWidth(PROFILE_IMAGE_WIDTH);
        profileImageView.setFitHeight(PROFILE_IMAGE_HEIGHT);
        profileImageView.imageProperty().bind(viewModel.profileImageProperty());
        var clip = new Rectangle();
        clip.widthProperty().bind(profileImageView.fitWidthProperty());
        clip.heightProperty().bind(profileImageView.fitHeightProperty());
        clip.setArcWidth(PROFILE_IMAGE_WIDTH);
        clip.setArcHeight(PROFILE_IMAGE_HEIGHT);
        profileImageView.setClip(clip);
        profileImageView.effectProperty().bind(viewModel.liveProperty().map(live -> live ? null : new SepiaTone()));

        userNameLabel = new Label();
        userNameLabel.getStyleClass().add(USER_NAME_STYLE_CLASS);
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        loginLabel = new Label();
        loginLabel.getStyleClass().add(USER_NAME_STYLE_CLASS);
        loginLabel.textProperty().bind(viewModel.userLoginProperty().map(s -> "(" + s + ")"));
        loginLabel.visibleProperty().bind(viewModel.userLoginProperty().isEqualToIgnoreCase(viewModel.userNameProperty()).not());

        gameTitleLabel = new Label();
        gameTitleLabel.getStyleClass().add(GAME_TITLE_STYLE_CLASS);
        gameTitleLabel.textProperty().bind(viewModel.gameNameProperty());

        onlineMark = new Circle(4);
        onlineMark.getStyleClass().add(ONLINE_MARK_STYLE_CLASS);

        viewerCountLabel = new Label();
        viewerCountLabel.textProperty().bind(viewModel.viewerCountProperty().asString("%,d"));

        gameTitleLabel.visibleProperty().bind(viewModel.liveProperty());
        gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());
        onlineMark.visibleProperty().bind(viewModel.liveProperty());
        onlineMark.managedProperty().bind(onlineMark.visibleProperty());
        viewerCountLabel.visibleProperty().bind(viewModel.liveProperty());
        viewerCountLabel.managedProperty().bind(viewerCountLabel.visibleProperty());

        // TODO 名前、ゲームタイトルが縮小されるように

        // 表示名とユーザーIDのコンテナ
        var names = new HBox(userNameLabel, loginLabel);
        names.setSpacing(4);

        // ユーザー名とゲーム名のコンテナ
        var center = new VBox(names, gameTitleLabel);
        center.getStyleClass().add(NAMES_CONTAINER_CLASS);
        center.setAlignment(Pos.CENTER_LEFT);
        center.setFillWidth(true);

        // 視聴者数とオンラインアイコン
        var right = new HBox(viewerCountLabel, onlineMark);
        right.setSpacing(10);
        right.setPadding(new Insets(0, 10, 0, 0));
        right.setAlignment(Pos.CENTER_RIGHT);

        BorderPane.setAlignment(profileImageView, Pos.CENTER);

        setLeft(profileImageView);
        setCenter(center);
        setRight(right);
    }
}
