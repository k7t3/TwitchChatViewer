package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PostedClipViewModel implements ViewModel {

    private final ReadOnlyIntegerWrapper times = new ReadOnlyIntegerWrapper(0);

    private final Set<Broadcaster> postedChannels = new HashSet<>();

    private final ReadOnlyObjectWrapper<VideoClip> clip = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper creator = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper thumbnailUrl = new ReadOnlyStringWrapper();

    private final ReadOnlyObjectWrapper<LocalDateTime> lastPostedAt = new ReadOnlyObjectWrapper<>();

    private final PostedClipRepository repository;

    public PostedClipViewModel(VideoClip clip, Broadcaster broadcaster, PostedClipRepository repository) {
        this.times.set(1);
        this.clip.set(clip);
        this.postedChannels.add(broadcaster);
        this.title.set(clip.title());
        this.creator.set(clip.creatorName());
        this.thumbnailUrl.set(clip.thumbnailUrl());
        lastPostedAt.set(LocalDateTime.now());
        this.repository = repository;
    }

    public void remove() {
        repository.getPostedClips().remove(clip.get().id());
    }

    public void onPosted(Broadcaster broadcaster) {
        postedChannels.add(broadcaster);
        lastPostedAt.set(LocalDateTime.now());
        times.set(times.get() + 1);
    }

    Set<Broadcaster> getPostedChannels() {
        return postedChannels;
    }

    public boolean isPosted(Broadcaster broadcaster) {
        return postedChannels.contains(broadcaster);
    }

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

        TaskWorker.getInstance().submit(task);

        return task;
    }

    public void copyClipURL() {
        var clip = getClip();

        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, clip.url()));
    }

    public Image getThumbnailImage() {
        return repository.getThumbnailStore().get(this);
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyIntegerProperty timesProperty() { return times.getReadOnlyProperty(); }
    public int getTimes() { return times.get(); }

    public ReadOnlyObjectProperty<VideoClip> clipProperty() { return clip.getReadOnlyProperty(); }
    public VideoClip getClip() { return clip.get(); }

    public ReadOnlyObjectProperty<LocalDateTime> lastPostedAtProperty() { return lastPostedAt.getReadOnlyProperty(); }
    public LocalDateTime getLastPostedAt() { return lastPostedAt.get(); }

    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }

    public ReadOnlyStringProperty creatorProperty() { return creator.getReadOnlyProperty(); }
    public String getCreator() { return creator.get(); }

    public ReadOnlyStringProperty thumbnailUrlProperty() { return thumbnailUrl.getReadOnlyProperty(); }
    public String getThumbnailUrl() { return thumbnailUrl.get(); }
}
