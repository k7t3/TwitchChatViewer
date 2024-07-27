/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.chat.ChannelChatBadgeStore;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class TwitchChannelViewModel {

    private static final String CHANNEL_URL_FORMAT = "https://www.twitch.tv/%s";

    private final ReadOnlyObjectWrapper<Broadcaster> broadcaster;
    private ReadOnlyObjectWrapper<LazyImage> profileImage;
    private ReadOnlyObjectWrapper<Image> offlineImage;
    private ReadOnlyObjectWrapper<StreamInfo> streamInfo;
    private ReadOnlyBooleanWrapper live;
    private ReadOnlyBooleanWrapper chatJoined;
    private final ReadOnlyBooleanWrapper following;
    private final BooleanProperty persistent;

    private final ChannelChatBadgeStore chatBadgeStore;
    private final TwitchChannel channel;
    private final ChannelViewModelRepository repository;

    public TwitchChannelViewModel(TwitchChannel channel, ChannelViewModelRepository repository) {
        this.channel = channel;
        this.repository = repository;
        broadcaster = new ReadOnlyObjectWrapper<>(DEMOBroadcasterProvider.provide(channel.getBroadcaster()));
        chatBadgeStore = new ChannelChatBadgeStore(channel);
        following = new ReadOnlyBooleanWrapper(channel.isFollowing());
        persistent = new SimpleBooleanProperty(channel.isPersistent()) {
            @Override
            protected void invalidated() {
                channel.setPersistent(super.get());
            }
        };

        if (channel.isStreaming()) {
            updateStreamInfo(channel.getStream());
        }
    }

    public void updateStreamInfo(StreamInfo streamInfo) {
        var info = DEMOStreamInfoProvider.provide(streamInfo);
        streamInfoWrapper().set(info);
        liveWrapper().set(info != null);
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    /**
     * チャンネルのページをブラウザで開く。
     * <p>
     *     開かれるブラウザはAWTの実装に基づく。
     * </p>
     */
    public void openChannelPageOnBrowser() {
        var login = getBroadcaster().getUserLogin();

        var desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return;
        }

        try {
            desktop.browse(new URI(CHANNEL_URL_FORMAT.formatted(login)));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public FXTask<ChatRoom> joinChatAsync() {
        if (isChatJoined()) return FXTask.of(channel.getOrJoinChatRoom());

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();
            return channel.getOrJoinChatRoom();
        });
        task.onDone(() -> chatJoinedWrapper().set(true));
        task.runAsync();
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (!isChatJoined()) return FXTask.empty();

        if (chatJoined != null) {
            chatJoined.set(false);
        }

        var task = FXTask.task(() -> {
            channel.leaveChat();
            repository.releaseChannel(this);
        });
        task.runAsync();

        return task;
    }

    public ChannelChatBadgeStore getChatBadgeStore() {
        return chatBadgeStore;
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<Broadcaster> broadcasterProperty() { return broadcaster.getReadOnlyProperty(); }
    public Broadcaster getBroadcaster() { return broadcaster.get(); }

    public ObservableValue<String> observableUserLogin() { return broadcaster.map(Broadcaster::getUserLogin); }
    public String getUserLogin() { return broadcaster.get().getUserLogin(); }

    public ObservableValue<String> observableUserName() { return broadcaster.map(Broadcaster::getUserName); }
    public String getUserName() { return broadcaster.get().getUserName(); }

    public ObservableValue<String> observableStreamTitle() { return streamInfoWrapper().map(StreamInfo::title).orElse(""); }
    public String getStreamTitle() { return streamInfo == null ? "" : observableStreamTitle().getValue(); }

    public ObservableValue<Integer> observableViewerCount() { return streamInfoWrapper().map(StreamInfo::viewerCount).orElse(-1); }
    public int getViewerCount() { return streamInfo == null ? -1 : observableViewerCount().getValue(); }

    public ObservableValue<String> observableGameName() { return streamInfoWrapper().map(StreamInfo::gameName).orElse(""); }
    public String getGameName() { return streamInfo == null ? "" : observableGameName().getValue(); }

    private ReadOnlyObjectWrapper<LazyImage> profileImageWrapper() {
        if (profileImage == null) {
            profileImage = new ReadOnlyObjectWrapper<>(new LazyImage(
                    channel.getBroadcaster().getProfileImageUrl(),
                    64,
                    64
            ));
        }
        return profileImage;
    }
    public ReadOnlyObjectProperty<LazyImage> profileImageProperty() { return profileImageWrapper().getReadOnlyProperty(); }
    public LazyImage getProfileImage() { return profileImageWrapper().get(); }

    private ReadOnlyObjectWrapper<Image> offlineImageWrapper() {
        if (offlineImage == null) {
            offlineImage = new ReadOnlyObjectWrapper<>();
            channel.getBroadcaster().getOfflineImageUrl().ifPresent(url -> offlineImage.set(new Image(url, true)));
        }
        return offlineImage;
    }
    public ReadOnlyObjectProperty<Image> offlineImageProperty() { return offlineImageWrapper().getReadOnlyProperty(); }
    public Image getOfflineImage() { return offlineImageWrapper().get(); }

    private ReadOnlyObjectWrapper<StreamInfo> streamInfoWrapper() {
        if (streamInfo == null) {
            streamInfo = new ReadOnlyObjectWrapper<>();
        }
        return streamInfo;
    }
    public ReadOnlyObjectProperty<StreamInfo> streamInfoProperty() { return streamInfoWrapper().getReadOnlyProperty(); }
    public StreamInfo getStreamInfo() { return streamInfoWrapper().get(); }

    private ReadOnlyBooleanWrapper liveWrapper() {
        if (live == null) {
            live = new ReadOnlyBooleanWrapper();
        }
        return live;
    }
    public ReadOnlyBooleanProperty liveProperty() { return liveWrapper().getReadOnlyProperty(); }
    public boolean isLive() { return live != null && live.get(); }

    private ReadOnlyBooleanWrapper chatJoinedWrapper() {
        if (chatJoined == null) {
            chatJoined = new ReadOnlyBooleanWrapper();
        }
        return chatJoined;
    }
    public ReadOnlyBooleanProperty chatJoinedProperty() { return chatJoinedWrapper().getReadOnlyProperty(); }
    public boolean isChatJoined() { return chatJoined != null && chatJoined.get(); }

    public ReadOnlyBooleanProperty followingProperty() { return following.getReadOnlyProperty(); }
    public boolean isFollowing() { return following.get(); }

    public BooleanProperty persistentProperty() { return persistent; }
    public boolean isPersistent() { return persistent.get(); }
    public void setPersistent(boolean persistent) { persistentProperty().set(persistent); }
}
