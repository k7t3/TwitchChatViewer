package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.core.ExceptionHandler;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental
 */
public class ClipPlayerViewModel implements ViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipPlayerViewModel.class);

    private static final String REPLACEMENT_REGEX = "-preview-\\d+x\\d+\\.(jpg|png|gif)$";

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private ObjectProperty<PostedClipViewModel> clip;

    private ReadOnlyObjectWrapper<Media> media;

    private ReadOnlyObjectWrapper<MediaPlayer> player;

    public ClipPlayerViewModel(PostedClipViewModel clip) {
        setClip(clip);
    }

    private String getEstimatedMediaSource(PostedClipViewModel clip) {
        // クリップのサムネイルURLからメディアのURLを推定する
        var source = clip.getThumbnailUrl().replaceAll(REPLACEMENT_REGEX, ".mp4");

        LOGGER.info("estimated clip media source [{}]", source);

        return source;
    }

    public void play() {
        var player = getPlayer();

        // 最後まで再生していた場合は先頭に戻す
        if (player.getCurrentTime().equals(player.getTotalDuration())) {
            player.seek(Duration.ZERO);
        }

        player.play();
    }

    public void pause() {
        getPlayer().pause();
    }

    public void dispose() {
        if (player != null) {
            LOGGER.info("player disposed");
            player.get().dispose();
        }
    }

    // ******************** PROPERTIES ********************


    private ReadOnlyBooleanWrapper loadedWrapper() { return loaded; }
    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }
    public void setLoaded(boolean loaded) { this.loaded.set(loaded); }

    public ObjectProperty<PostedClipViewModel> clipProperty() {
        if (clip == null) clip = new SimpleObjectProperty<>();
        return clip;
    }
    public PostedClipViewModel getClip() { return clipProperty().get(); }
    public void setClip(PostedClipViewModel clip) { clipProperty().set(clip); }

    private ReadOnlyObjectWrapper<Media> mediaWrapper() {
        if (media == null) {
            var clip = getClip();
            if (clip == null) throw new IllegalStateException();
            media = new ReadOnlyObjectWrapper<>(new Media(getEstimatedMediaSource(clip)));
        }
        return media;
    }
    public ReadOnlyObjectProperty<Media> mediaProperty() { return mediaWrapper().getReadOnlyProperty(); }
    public Media getMedia() { return mediaWrapper().get(); }

    private ReadOnlyObjectWrapper<MediaPlayer> playerWrapper() {
        if (player == null) {
            var player = new MediaPlayer(getMedia());
            player.setOnReady(() -> loaded.set(true));
            player.setOnError(() -> ExceptionHandler.handle(player.getError()));
            this.player = new ReadOnlyObjectWrapper<>(player);
        }
        return player;
    }
    private ReadOnlyObjectProperty<MediaPlayer> playerProperty() { return playerWrapper().getReadOnlyProperty(); }
    public MediaPlayer getPlayer() { return playerWrapper().get(); }

}
