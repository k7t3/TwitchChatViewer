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
import com.github.k7t3.tcv.app.service.FXTask;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
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

public class FoundChannelListCell extends ListCell<FoundChannelViewModel> implements AutoCloseable {
    private final java.util.function.Supplier<Alert> alerts;
    private boolean closed;
    private BrowserWatch browserWatch;
    private Alert activeAlert;
    private final java.util.List<Button> actions = new java.util.ArrayList<>();


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

    public FoundChannelListCell() { this(() -> new Alert(Alert.AlertType.WARNING)); }
    FoundChannelListCell(java.util.function.Supplier<Alert> alerts) {
        this.alerts = alerts;
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
        openChatButton.setOnAction(e -> { if (!closed && !isEmpty() && getItem() != null) getItem().joinChatAsync(); });

        var openBrowser = new Button("", new FontIcon(FontAwesomeSolid.GLOBE));
        openBrowser.getStyleClass().addAll(Styles.BUTTON_ICON);
        openBrowser.setTooltip(new Tooltip(Resources.getString("search.open.browser")));
        openBrowser.setOnAction(e -> {
            var item = getItem();
            if (closed || item == null || isEmpty()) return;
            var task = item.openChannelPageOnBrowser();
            if (closed || getItem() != item || isEmpty()) return;
            if (browserWatch != null) browserWatch.close();
            browserWatch = new BrowserWatch(task, item);
            browserWatch.attach();
        });
        actions.addAll(java.util.List.of(openChatButton, openBrowser));
        actions.forEach(button -> button.setAccessibleText(button.getTooltip().getText()));

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
        if (closed || empty || item != getItem()) detachResult();
        clearImage();
        super.updateItem(item, empty);
        if (closed || item == null || empty) {
            setGraphic(null);
            return;
        }
        if (layout == null) initialize();
        update(item);
        setGraphic(layout);
    }

    private void clearImage() {
        if (profileImageView != null) { profileImageView.imageProperty().unbind(); profileImageView.setImage(null); }
    }

    private void detachResult() {
        if (browserWatch != null) browserWatch.close();
        var alert = activeAlert;
        activeAlert = null;
        if (alert != null) { alert.setOnHidden(null); alert.hide(); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        detachResult();
        actions.forEach(button -> { button.setOnAction(null); if (button.getTooltip() != null) button.getTooltip().hide(); });
        clearImage();
        if (profileImageView != null) {
            profileImageView.effectProperty().unbind();
            profileImageView.setEffect(null);
            if (profileImageView.getClip() instanceof Rectangle clip) { clip.widthProperty().unbind(); clip.heightProperty().unbind(); }
        }
        if (gameTitleLabel != null) { gameTitleLabel.visibleProperty().unbind(); gameTitleLabel.managedProperty().unbind(); }
        updateItem(null, true);
    }

    private final class BrowserWatch implements AutoCloseable {
        final FXTask<Boolean> task;
        final FoundChannelViewModel item;
        final EventHandler<WorkerStateEvent> completed = event -> finish(event.getEventType() == WorkerStateEvent.WORKER_STATE_SUCCEEDED);
        BrowserWatch(FXTask<Boolean> task, FoundChannelViewModel item) { this.task = task; this.item = item; }
        void attach() {
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, completed);
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, completed);
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, completed);
            var state = task.getState();
            if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED) finish(state == Worker.State.SUCCEEDED);
        }
        void finish(boolean succeeded) {
            close();
            if (!succeeded || closed || isEmpty() || getItem() != item || item.isClosed() || Boolean.TRUE.equals(task.getValue())) return;
            var alert = alerts.get();
            activeAlert = alert;
            alert.setTitle("Warning");
            alert.setHeaderText("Failed to open Browser!");
            alert.setContentText("Failed to Open Browser!");
            alert.setOnHidden(event -> { if (activeAlert == alert) activeAlert = null; });
            alert.show();
        }
        @Override public void close() {
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, completed);
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, completed);
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, completed);
            if (browserWatch == this) browserWatch = null;
        }
    }
}
