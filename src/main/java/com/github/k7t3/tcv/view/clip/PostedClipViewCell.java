package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.image.LazyImageView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class PostedClipViewCell extends ListCell<PostedClipItem> {

    private HBox layout;

    private LazyImageView thumbnail;

    private Label title;

    private Label description;

    private final BooleanProperty unknown = new SimpleBooleanProperty(false);

    public PostedClipViewCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initialize() {
        thumbnail = new LazyImageView();
        thumbnail.setFitWidth(64);
        thumbnail.setFitHeight(64);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);
        thumbnail.setMouseTransparent(true);

        title = new Label();
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TITLE_4);
        title.setPrefWidth(USE_COMPUTED_SIZE);

        description = new Label();
        description.getStyleClass().addAll(Styles.TEXT_SMALL);
        description.setMinWidth(20);
        description.setPrefWidth(USE_COMPUTED_SIZE);

        var vbox = new VBox(title, description);
        vbox.setSpacing(2);
        vbox.setPadding(new Insets(0, 0, 0, 10));
        vbox.setMouseTransparent(true);
        vbox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(vbox, Priority.ALWAYS);

        var browseButton = new Button("", new FontIcon(FontAwesomeSolid.GLOBE));
        browseButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ACCENT);
        browseButton.setTooltip(new Tooltip(Resources.getString("clip.open.browser")));
        browseButton.setOnAction(e -> {
            getItem().browseClipPageAsync();
        });

        var clipURLButton = new Button("", new FontIcon(FontAwesomeSolid.COPY));
        clipURLButton.getStyleClass().addAll(Styles.BUTTON_ICON);
        clipURLButton.setTooltip(new Tooltip(Resources.getString("clip.copy.link")));
        clipURLButton.setOnAction(e -> getItem().copyClipURL());

        var removeButton = new Button("", new FontIcon(FontAwesomeSolid.TRASH));
        removeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.DANGER);
        removeButton.setTooltip(new Tooltip(Resources.getString("clip.remove")));
        removeButton.setOnAction(e -> getItem().remove());

        var retryButton = new Button("", new FontIcon(Feather.REFRESH_CW));
        retryButton.getStyleClass().addAll(Styles.BUTTON_ICON);
        retryButton.setTooltip(new Tooltip(Resources.getString("clip.retry.tooltip")));
        retryButton.visibleProperty().bind(unknown);
        retryButton.setOnAction(e -> {
            var item = getItem();
            retryButton.setDisable(true);
            var async = item.retry();
            async.setFinally(() -> retryButton.setDisable(false));
            async.onDone(() -> update(item, false));
        });

        vbox.maxWidthProperty().bind(widthProperty()
                .subtract(thumbnail.fitWidthProperty().multiply(2))
                .subtract(browseButton.widthProperty())
                .subtract(clipURLButton.widthProperty())
                .subtract(removeButton.widthProperty())
                .subtract(retryButton.widthProperty())
        );

        layout = new HBox(thumbnail, vbox, browseButton, clipURLButton, removeButton, retryButton);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setSpacing(4);

        setPrefHeight(64);
    }

    private void update(PostedClipItem item, boolean empty) {
        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        var clip = item.getClip();

        if (clip != null) {
            unknown.set(false);
            thumbnail.setLazyImage(item.getThumbnailImage());
            title.setText(item.getTitle());
            description.setText("Creator: %s  Posted: %d times".formatted(item.getCreator(), item.getTimes()));
        } else {
            unknown.set(true);
            thumbnail.setLazyImage(null);
            thumbnail.setImage(Resources.getQuestionImage());
            title.setText("UNKNOWN");
            description.setText(item.getUrl());
        }
        setGraphic(layout);
    }

    @Override
    protected void updateItem(PostedClipItem item, boolean empty) {
        super.updateItem(item, empty);
        update(item, empty);
    }
}
