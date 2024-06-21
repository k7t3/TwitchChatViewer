package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

public class PostedClipViewModel extends AbstractPostedClip implements ViewModel {

    private final ReadOnlyObjectWrapper<VideoClip> clip = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper creator = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper thumbnailUrl = new ReadOnlyStringWrapper();

    private final ReadOnlyObjectWrapper<LocalDateTime> createdAt = new ReadOnlyObjectWrapper<>();

    private final PostedClipRepository repository;

    public PostedClipViewModel(VideoClip clip, Broadcaster broadcaster, PostedClipRepository repository) {
        this.times.set(1);
        this.clip.set(clip);
        this.postedChannels.add(broadcaster);
        this.title.set(clip.title());
        this.creator.set(clip.creatorName());
        this.thumbnailUrl.set(clip.thumbnailUrl());
        this.createdAt.set(clip.createdAt());
        this.lastPostedAt.set(LocalDateTime.now());
        this.repository = repository;
    }

    @Override
    public void remove() {
        repository.getPostedClips().remove(clip.get().id());
    }

    @Override
    public FXTask<Boolean> openClipPageOnBrowser() {
        var clip = getClip();

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(clip.url()));
            return true;
        });

        task.runAsync();

        return task;
    }

    @Override
    public void copyClipURL() {
        var clip = getClip();

        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, clip.url()));
    }

    public LazyImage getThumbnailImage() {
        return repository.getThumbnailStore().get(this);
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<VideoClip> clipProperty() { return clip.getReadOnlyProperty(); }
    public VideoClip getClip() { return clip.get(); }

    public ReadOnlyObjectProperty<LocalDateTime> createdAtProperty() { return createdAt.getReadOnlyProperty(); }
    public LocalDateTime getCreatedAt() { return createdAt.get(); }

    public ReadOnlyObjectProperty<LocalDateTime> lastPostedAtProperty() { return lastPostedAt.getReadOnlyProperty(); }
    public LocalDateTime getLastPostedAt() { return lastPostedAt.get(); }

    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }

    public ReadOnlyStringProperty creatorProperty() { return creator.getReadOnlyProperty(); }
    public String getCreator() { return creator.get(); }

    public ReadOnlyStringProperty thumbnailUrlProperty() { return thumbnailUrl.getReadOnlyProperty(); }
    public String getThumbnailUrl() { return thumbnailUrl.get(); }
}
