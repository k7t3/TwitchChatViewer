package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class VideoClipViewCell extends ListCell<VideoClipViewModel> {

    private BorderPane layout;

    private ImageView thumbnail;

    private Label title;

    private Label description;

    public VideoClipViewCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initialize() {
        thumbnail = new ImageView();
        thumbnail.setFitWidth(48);
        thumbnail.setFitHeight(48);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);

        title = new Label();
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TITLE_4);

        description = new Label();
        description.getStyleClass().addAll(Styles.TEXT_SMALL);

        var vbox = new VBox(title, description);
        vbox.setSpacing(2);
        vbox.setPadding(new Insets(0, 0, 0, 10));

        layout = new BorderPane();
        layout.setCenter(vbox);
        BorderPane.setAlignment(thumbnail, Pos.CENTER);
        layout.setLeft(thumbnail);
        layout.setMouseTransparent(true);
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
