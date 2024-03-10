package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.PostedClipViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class PostedClipViewCell extends ListCell<PostedClipViewModel> {

    private HBox layout;

    private ImageView thumbnail;

    private Label title;

    private Label description;

    public PostedClipViewCell() {
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
        removeButton.setOnAction(e -> getItem().remove());

        layout = new HBox(thumbnail, vbox, openBrowser, clipURLButton, removeButton);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setSpacing(4);

        setPrefHeight(64);
    }

    @Override
    protected void updateItem(PostedClipViewModel item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (layout == null) {
            initialize();
        }

        thumbnail.setImage(item.getThumbnailImage());
        title.setText(item.getTitle());
        description.setText("Creator: %s  Posted: %d times".formatted(item.getCreator(), item.getTimes()));
        setGraphic(layout);
    }
}
