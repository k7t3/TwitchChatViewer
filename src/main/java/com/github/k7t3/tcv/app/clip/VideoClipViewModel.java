package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.domain.clip.PostedClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.image.Image;

/**
 * チャットで投稿されたクリップ
 */
public class VideoClipViewModel implements ViewModel {

    private static final double THUMBNAIL_WIDTH = 200;
    private static final double THUMBNAIL_HEIGHT = 200;

    private final ReadOnlyObjectWrapper<PostedClip> posted;

    private final ReadOnlyStringWrapper title;

    private final ReadOnlyStringWrapper creator;

    private ReadOnlyObjectWrapper<Image> thumbnail;

    public VideoClipViewModel(PostedClip posted) {
        this.posted = new ReadOnlyObjectWrapper<>(posted);
        this.title = new ReadOnlyStringWrapper(posted.getClip().title());
        this.creator = new ReadOnlyStringWrapper(posted.getClip().creatorName());
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<PostedClip> postedProperty() { return posted.getReadOnlyProperty(); }
    public PostedClip getPosted() { return posted.get(); }

    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }

    public ReadOnlyStringProperty creatorProperty() { return creator.getReadOnlyProperty(); }
    public String getCreator() { return creator.get(); }

    private ReadOnlyObjectWrapper<Image> thumbnailWrapper() {
        if (thumbnail == null) {
            var url = getPosted().getClip().thumbnailUrl();
            thumbnail = new ReadOnlyObjectWrapper<>(new Image(url, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true, true, true));
        }
        return thumbnail;
    }
    public ReadOnlyObjectProperty<Image> thumbnailProperty() { return thumbnailWrapper().getReadOnlyProperty(); }
    public Image getThumbnail() { return thumbnailWrapper().get(); }


}
