package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.core.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ClipPostedAppEvent;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;
import com.github.k7t3.tcv.domain.event.chat.ClipPostedEvent;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;

public class PostedClipRepository extends AbstractViewModel {

    /**
     * クリップのIDをキーとするクリップのMap
     */
    private final ObservableMap<String, PostedClipViewModel> postedClips = FXCollections.observableHashMap();

    /**
     * クリップのIDと思しき文字列をキーとするクリップと思しきもののMap
     */
    private final ObservableMap<String, EstimatedClipViewModel> estimatedClipURLs = FXCollections.observableHashMap();

    private ClipThumbnailStore thumbnailStore;

    private FlowableSubscriber<ChatRoomEvent> subscriber;

    public PostedClipRepository() {
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        subscriber = new DownCastFXSubscriber<>(ClipPostedEvent.class, this::onClipPostedEvent);
        eventSubscribers.subscribeChatEvent(subscriber);
    }

    private void onClipPostedEvent(ClipPostedEvent e) {
        var chatRoom = e.getChatRoom();
        var broadcaster = chatRoom.getBroadcaster();
        var clipChatMessage = e.getClipChatMessage();
        var c = clipChatMessage.getClip();

        if (c.isPresent()) {
            var clip = c.get();
            Platform.runLater(() -> {
                var posted = postedClips.get(clip.id());
                if (posted == null) {
                    posted = new PostedClipViewModel(clip, broadcaster, this);
                    postedClips.put(clip.id(), posted);
                } else {
                    posted.onPosted(broadcaster);
                }
                publishEvent();
            });
        } else {
            Platform.runLater(() -> {
                var url = estimatedClipURLs.get(clipChatMessage.getEstimatedURL());
                if (url == null) {
                    url = new EstimatedClipViewModel(this, clipChatMessage);
                    estimatedClipURLs.put(clipChatMessage.getEstimatedURL(), url);
                } else {
                    url.onPosted(broadcaster);
                }
                publishEvent();
            });
        }
    }

    private void publishEvent() {
        publish(new ClipPostedAppEvent());
    }

    public NumberBinding getCountBinding() {
        return Bindings.size(postedClips).add(Bindings.size(estimatedClipURLs));
    }

    public List<Broadcaster> getPostedBroadcasters() {
        return postedClips.values()
                .stream()
                .flatMap(p -> p.getPostedChannels().stream())
                .distinct()
                .toList();
    }

    public ObservableMap<String, PostedClipViewModel> getPostedClips() {
        return postedClips;
    }

    public ObservableMap<String, EstimatedClipViewModel> getEstimatedClipURLs() {
        return estimatedClipURLs;
    }

    ClipThumbnailStore getThumbnailStore() {
        if (thumbnailStore == null) {
            thumbnailStore = new ClipThumbnailStore();
        }
        return thumbnailStore;
    }

    @Override
    public void onLogout() {
        if (subscriber != null) {
            subscriber.cancel();
        }
        postedClips.clear();
        estimatedClipURLs.clear();
    }

    @Override
    public void close() {
        // no-op
    }
}
