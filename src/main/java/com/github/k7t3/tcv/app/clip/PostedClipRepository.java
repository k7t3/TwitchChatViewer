package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.*;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

import java.util.HashSet;
import java.util.List;

public class PostedClipRepository implements ViewModel, ChatRoomListener {

    /**
     * クリップのIDをキーとするクリップのMap
     */
    private final ObservableMap<String, PostedClipViewModel> postedClips = FXCollections.observableHashMap();

    /**
     * クリップのIDと思しき文字列をキーとするクリップと思しきもののMap
     */
    private final ObservableMap<String, EstimatedClipViewModel> estimatedClipURLs = FXCollections.observableHashMap();

    private ClipThumbnailStore thumbnailStore;

    public PostedClipRepository() {
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

    public void clear() {
        postedClips.clear();
        estimatedClipURLs.clear();
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, ClipChatMessage clipChatMessage) {

        var broadcaster = chatRoom.getBroadcaster();

        if (clipChatMessage.getClip().isPresent()) {

            var clip = clipChatMessage.getClip().get();
            Platform.runLater(() -> {
                var posted = postedClips.get(clip.id());
                if (posted == null) {
                    posted = new PostedClipViewModel(clip, broadcaster, this);
                    postedClips.put(clip.id(), posted);
                } else {
                    posted.onPosted(broadcaster);
                }
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
            });
        }
    }

    @Override
    public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
        // no-op
    }

    @Override
    public void onChatCleared(ChatRoom chatRoom) {
        // no-op
    }

    @Override
    public void onChatMessageDeleted(ChatRoom chatRoom, String messageId) {
        // no-op
    }

    @Override
    public void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active) {
        // no-op
    }

    @Override
    public void onRaidReceived(ChatRoom chatRoom, String raiderName, int viewerCount) {
        // no-op
    }

    @Override
    public void onUserSubscribed(ChatRoom chatRoom, ChatData chatData) {
        // no-op
    }

    @Override
    public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        // no-op
    }
}
