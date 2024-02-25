package com.github.k7t3.tcv.view.clip;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class VideoClipViewCell extends ListCell<VideoClipViewModel> {

    private HBox layout;

    private ImageView thumbnail;

    private Label title;

    private Label description;

    public VideoClipViewCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initialize() {
        thumbnail = new ImageView();
        thumbnail.setFitWidth(64);
        thumbnail.setFitHeight(64);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);
        thumbnail.setMouseTransparent(true);

        title = new Label();
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TITLE_4);
        title.setMinWidth(20);

        description = new Label();
        description.getStyleClass().addAll(Styles.TEXT_SMALL);
        description.setMinWidth(20);

        var vbox = new VBox(title, description);
        vbox.setSpacing(2);
        vbox.setPadding(new Insets(0, 0, 0, 10));
        vbox.setMouseTransparent(true);
        vbox.setMinWidth(20);
        vbox.setAlignment(Pos.CENTER_LEFT);
        vbox.setMinWidth(1);

        var openBrowser = new Button("", new FontIcon(FontAwesomeSolid.GLOBE));
        openBrowser.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ACCENT);
        openBrowser.setTooltip(new Tooltip(Resources.getString("clip.open.browser")));
        openBrowser.setOnAction(e -> {
            var task = getItem().openClipPageOnBrowser();
            task.setOnSucceeded(e2 -> {
                if (task.getValue()) return;

                var alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Failed to open Browser!");
                alert.setContentText("Failed to Open Browser!");
                alert.show();
            });
        });

        var clipURLButton = new Button("", new FontIcon(FontAwesomeSolid.COPY));
        clipURLButton.getStyleClass().addAll(Styles.BUTTON_ICON);
        clipURLButton.setTooltip(new Tooltip(Resources.getString("clip.copy.link")));
        clipURLButton.setOnAction(e -> getItem().copyClipURL());

        var removeButton = new Button("", new FontIcon(FontAwesomeSolid.TRASH));
        removeButton.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.DANGER);
        removeButton.setTooltip(new Tooltip(Resources.getString("clip.remove")));
        removeButton.setOnAction(e -> getItem().removeAsync());

        layout = new HBox(thumbnail, vbox, new Spacer(), openBrowser, clipURLButton, removeButton);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setSpacing(4);

        setPrefHeight(64);
    }

    @Override
    protected void updateItem(VideoClipViewModel item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        thumbnail.setImage(item.getThumbnail());
        title.setText(item.getTitle());
        description.setText("Creator: %s  Posted: %d times".formatted(item.getCreator(), item.getPosted().getPostCount()));
        setGraphic(layout);
    }
}
