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

package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.channel.FoundChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class FoundChannelListCell extends ListCell<FoundChannelViewModel> {

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private static final String STYLE_CLASS = "follow-channel-view";
    private static final String PROFILE_IMAGE_STYLE_CLASS = "profile-image-view";
    private static final String NAMES_CONTAINER_CLASS = "names-container";
    private static final String USER_NAME_STYLE_CLASS = "user-name-label";
    private static final String GAME_TITLE_STYLE_CLASS = "game-title-label";

    private HBox layout;

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
        userNameLabel.setMinWidth(20);

        gameTitleLabel = new Label();
        gameTitleLabel.getStyleClass().add(GAME_TITLE_STYLE_CLASS);
        gameTitleLabel.setMinWidth(20);

        gameTitleLabel.visibleProperty().bind(live);
        gameTitleLabel.managedProperty().bind(gameTitleLabel.visibleProperty());

        // ユーザー名とゲーム名のコンテナ
        var center = new VBox(userNameLabel, gameTitleLabel);
        center.getStyleClass().add(NAMES_CONTAINER_CLASS);
        center.setAlignment(Pos.CENTER_LEFT);
        center.setPadding(new Insets(0, 0, 0, 4));
        center.setMinWidth(20);
        HBox.setHgrow(center, Priority.ALWAYS);

        var openChatButton = new Button("", new FontIcon(FontAwesomeRegular.COMMENT_DOTS));
        openChatButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ACCENT);
        openChatButton.setTooltip(new Tooltip(Resources.getString("search.open.chat")));
        openChatButton.setOnAction(e -> getItem().joinChatAsync());

        var openBrowser = new Button("", new FontIcon(FontAwesomeSolid.GLOBE));
        openBrowser.getStyleClass().addAll(Styles.BUTTON_ICON);
        openBrowser.setTooltip(new Tooltip(Resources.getString("search.open.browser")));
        openBrowser.setOnAction(e -> {
            var task = getItem().openChannelPageOnBrowser();
            task.setOnSucceeded(e2 -> {
                if (task.getValue()) return;

                var alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Failed to open Browser!");
                alert.setContentText("Failed to Open Browser!");
                alert.show();
            });
        });

        layout = new HBox(profileImageView, center, new Spacer(), openChatButton, openBrowser);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setSpacing(4);
        layout.setPrefWidth(HBox.USE_PREF_SIZE);
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
