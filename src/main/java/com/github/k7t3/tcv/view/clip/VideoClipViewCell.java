package com.github.k7t3.tcv.view.clip;

import atlantafx.base.controls.Tile;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;

public class VideoClipViewCell extends ListCell<VideoClipViewModel> {

    private Tile layout;

    private ImageView thumbnail;

    public VideoClipViewCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initialize() {
        thumbnail = new ImageView();
        thumbnail.setFitWidth(40);
        thumbnail.setFitHeight(40);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);

        layout = new Tile();
        layout.setGraphic(thumbnail);
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
        layout.setTitle(item.getTitle());
        layout.setDescription("Creator: %s".formatted(item.getCreator()));
        setGraphic(layout);
    }
}
