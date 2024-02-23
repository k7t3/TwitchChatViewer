package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.clip.PostedClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.net.URI;
import java.util.Map;

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

    public FXTask<Boolean> openClipPageOnBrowser() {
        var clip = posted.get().getClip();

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(clip.url()));
            return true;
        });

        TaskWorker.getInstance().submit(task);

        return task;
    }

    public void copyClipURL() {
        var clip = posted.get().getClip();

        var clipBoard = Clipboard.getSystemClipboard();
        clipBoard.setContent(Map.of(DataFormat.PLAIN_TEXT, clip.url()));
    }

    public void remove() {
        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        var repo = twitch.getClipRepository();

        var task = FXTask.task(() -> repo.remove(getPosted()));
        TaskWorker.getInstance().submit(task);
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
