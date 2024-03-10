package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;

public class PostedClipRepository implements ViewModel, ChatRoomListener {

    private final ObservableMap<String, PostedClipViewModel> postedClips = FXCollections.observableHashMap();

    private ClipThumbnailStore thumbnailStore;

    public PostedClipRepository() {
    }

    public IntegerBinding getCountBinding() {
        return Bindings.size(postedClips);
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

    ClipThumbnailStore getThumbnailStore() {
        if (thumbnailStore == null) {
            thumbnailStore = new ClipThumbnailStore();
        }
        return thumbnailStore;
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
        Platform.runLater(() -> {
            var broadcaster = chatRoom.getBroadcaster();
            var posted = postedClips.get(clip.id());
            if (posted == null) {
                posted = new PostedClipViewModel(clip, broadcaster, this);
                postedClips.put(clip.id(), posted);
            }
            posted.onPosted(broadcaster);
        });
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
    public void onUserSubscribed(ChatRoom chatRoom, String userName) {
        // no-op
    }

    @Override
    public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        // no-op
    }
}
