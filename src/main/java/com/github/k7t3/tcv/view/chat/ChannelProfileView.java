package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.view.image.LazyImageView;
import javafx.beans.value.ChangeListener;
import javafx.scene.effect.SepiaTone;
import javafx.scene.shape.Rectangle;

/** チャットヘッダーの画像・配信状態・配信情報 popup を所有する。画像の取得元は借用する。 */
final class ChannelProfileView extends LazyImageView implements AutoCloseable {
    private final ViewResources owned = new ViewResources();

    ChannelProfileView(TwitchChannelViewModel channel, double size) {
        setPreserveRatio(true); setSmooth(true); setFitWidth(size); setFitHeight(size);
        owned.add(() -> { lazyImageProperty().unbind(); setLazyImage(null); setEffect(null); setClip(null); });
        lazyImageProperty().bind(channel.profileImageProperty());
        var clip = new Rectangle();
        clip.widthProperty().bind(fitWidthProperty()); clip.heightProperty().bind(fitHeightProperty());
        clip.arcWidthProperty().bind(fitWidthProperty()); clip.arcHeightProperty().bind(fitHeightProperty());
        owned.add(() -> {
            clip.widthProperty().unbind(); clip.heightProperty().unbind(); clip.arcWidthProperty().unbind(); clip.arcHeightProperty().unbind();
        });
        setClip(clip);
        ChangeListener<Boolean> changed = (observable, oldValue, live) -> { if (!owned.isClosed()) setEffect(live ? null : new SepiaTone()); };
        channel.liveProperty().addListener(changed);
        owned.add(() -> channel.liveProperty().removeListener(changed));
        setEffect(channel.isLive() ? null : new SepiaTone());
        var hover = ChatRoomViewUtils.installStreamInfoPopOver(channel, this); owned.add(hover::unsubscribe);
    }

    @Override public void close() { owned.close(); }
}
