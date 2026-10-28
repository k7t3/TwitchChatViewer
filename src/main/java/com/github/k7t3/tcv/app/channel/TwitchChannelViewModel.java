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

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.chat.ChannelChatBadgeStore;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class TwitchChannelViewModel implements AutoCloseable {

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
    private final ImageLoader images;
    private LazyImage offlineLoad;
    private boolean closed;
    private final ChannelChatConnection chatConnection;
    private final Consumer<Throwable> reportFailure;
    private final BrowserActions browsers;
    private FXTask<ChatRoom> observedJoin;
    private FXTask<Void> observedLeave;

    public TwitchChannelViewModel(TwitchChannel channel, ChannelViewModelRepository repository, ImageLoader images,
                          Executor executor, Consumer<Throwable> reportFailure) {
        this.channel = channel;
        this.images = images;
        this.reportFailure = reportFailure;
        browsers = new BrowserActions(executor, repository::trackChatOperation, reportFailure);
        chatConnection = new ChannelChatConnection(channel, () -> repository.releaseChannel(this), executor,
                repository::trackChatOperation);
        broadcaster = new ReadOnlyObjectWrapper<>(channel.getBroadcaster());
        chatBadgeStore = new ChannelChatBadgeStore(channel, images);
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
        streamInfoWrapper().set(streamInfo);
        liveWrapper().set(streamInfo != null);
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
        checkOpen();
        browsers.open(CHANNEL_URL_FORMAT.formatted(getBroadcaster().getUserLogin()));
    }

    public FXTask<ChatRoom> joinChatAsync() {
        checkOpen();
        var task = chatConnection.join();
        if (observedJoin == task) return task;
        observedJoin = task;
        task.onDone(() -> {
            if (!closed && chatConnection.isCurrentJoin(task)) chatJoinedWrapper().set(true);
        });
        task.onFailure(failure -> {
            if (!closed && chatConnection.isCurrentJoin(task)) reportFailure.accept(failure);
        });
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (chatJoined != null) chatJoined.set(false);
        var task = chatConnection.leave();
        if (observedLeave == task) return task;
        observedLeave = task;
        task.onFailure(failure -> {
            if (!closed && chatConnection.isCurrentLeave(task)) reportFailure.accept(failure);
        });
        return task;
    }

    public boolean isRetiring() { return closed || chatConnection.isRetiring(); }
    CompletionStage<Void> retirement() { return chatConnection.retirement(); }

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
        checkOpen();
        if (profileImage == null) {
            profileImage = new ReadOnlyObjectWrapper<>(loadOwnedImage(
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
        checkOpen();
        if (offlineImage == null) {
            offlineImage = new ReadOnlyObjectWrapper<>();
            channel.getBroadcaster().getOfflineImageUrl().ifPresent(url -> {
                offlineLoad = loadOwnedImage(url, 0, 0);
                offlineImage.bind(offlineLoad.valueProperty());
            });
        }
        return offlineImage;
    }
    public ReadOnlyObjectProperty<Image> offlineImageProperty() { return offlineImageWrapper().getReadOnlyProperty(); }
    public Image getOfflineImage() { return offlineImageWrapper().get(); }

    private LazyImage loadOwnedImage(String url, double width, double height) {
        checkOpen();
        var image = images.load(url, width, height);
        if (closed) {
            image.close();
            checkOpen();
        }
        return image;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("channel view model is closed");
    }

    /** FX 上で参加の取消と退出を要求し、所有画像・binding を解放する。通信の終了待ちはアプリ側が行う。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        var profile = profileImage == null ? null : profileImage.get();
        var offline = offlineLoad;
        offlineLoad = null;
        CloseActions.runAll(
                chatConnection::close,
                browsers::close,
                () -> { if (chatJoined != null) chatJoined.set(false); },
                () -> { if (profileImage != null) profileImage.set(null); },
                () -> {
                    if (offlineImage != null) {
                        offlineImage.unbind();
                        offlineImage.set(null);
                    }
                },
                () -> { if (profile != null) profile.close(); },
                () -> { if (offline != null) offline.close(); },
                chatBadgeStore::close);
    }

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
