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

    private final ObservableMap<String, PostedClipViewModel> postedClips = FXCollections.observableHashMap();

    /** Twitch APIで取得に失敗したURL*/
    private final ObservableSet<EstimatedClipURL> estimatedClipURLs = FXCollections.observableSet(new HashSet<>());

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

    public ObservableSet<EstimatedClipURL> getEstimatedClipURLs() {
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

        if (clipChatMessage.getClip().isPresent()) {

            var clip = clipChatMessage.getClip().get();
            Platform.runLater(() -> {
                var broadcaster = chatRoom.getBroadcaster();
                var posted = postedClips.get(clip.id());
                if (posted == null) {
                    posted = new PostedClipViewModel(clip, broadcaster, this);
                    postedClips.put(clip.id(), posted);
                } else {
                    posted.onPosted(broadcaster);
                }
            });

        } else {
            var estimatedClipURL = new EstimatedClipURL(clipChatMessage.getEstimatedURL());
            Platform.runLater(() -> estimatedClipURLs.add(estimatedClipURL));
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
