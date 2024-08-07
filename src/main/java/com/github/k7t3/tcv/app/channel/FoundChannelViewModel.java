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

import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import javafx.beans.property.*;
import javafx.scene.image.Image;

import java.awt.*;
import java.net.URI;

public class FoundChannelViewModel extends AbstractViewModel {

    private static final String CHANNEL_URL_FORMAT = "https://www.twitch.tv/%s";

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private final ReadOnlyObjectWrapper<Broadcaster> broadcaster;
    private final ReadOnlyObjectWrapper<Image> profileImage;
    private final ReadOnlyBooleanWrapper live;
    private final ReadOnlyStringWrapper gameName;
    private final ChannelViewModelRepository channelRepository;

    public FoundChannelViewModel(
            ChannelViewModelRepository channelRepository,
            FoundChannel channel
    ) {
        broadcaster = new ReadOnlyObjectWrapper<>();
        profileImage = new ReadOnlyObjectWrapper<>();
        live = new ReadOnlyBooleanWrapper();
        gameName = new ReadOnlyStringWrapper();
        this.channelRepository = channelRepository;
        update(channel);
    }

    public void update(FoundChannel channel) {
        setBroadcaster(channel.getBroadcaster());
        setProfileImage(new Image(channel.getBroadcaster().getProfileImageUrl(), PROFILE_IMAGE_WIDTH, PROFILE_IMAGE_HEIGHT, true, true, true));
        setGameName(channel.getGameName());
        setLive(channel.isLive());
    }

    public FXTask<Boolean> openChannelPageOnBrowser() {
        var login = getBroadcaster().getUserLogin();

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(CHANNEL_URL_FORMAT.formatted(login)));
            return true;
        });

        task.runAsync();

        return task;
    }

    public FXTask<?> joinChatAsync() {
        var broadcaster = getBroadcaster();
        var t = channelRepository.getChannelAsync(broadcaster);
        t.onDone(() -> {
            var channel = t.getValue();

            // チャットを開くイベントを発行
            var opening = new ChatOpeningEvent(channel);
            publish(opening);
        });
        t.runAsync();
        return t;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    @Override
    public void onLogout() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyObjectWrapper<Broadcaster> broadcasterWrapper() { return broadcaster; }
    public ReadOnlyObjectProperty<Broadcaster> broadcasterProperty() { return broadcaster.getReadOnlyProperty(); }
    public Broadcaster getBroadcaster() { return broadcaster.get(); }
    private void setBroadcaster(Broadcaster broadcaster) { this.broadcaster.set(broadcaster); }

    private ReadOnlyObjectWrapper<Image> profileImageWrapper() { return profileImage; }
    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImage.getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImage.get(); }
    private void setProfileImage(Image profileImage) { this.profileImage.set(profileImage); }

    private ReadOnlyBooleanWrapper liveWrapper() { return live; }
    public ReadOnlyBooleanProperty liveProperty() { return live.getReadOnlyProperty(); }
    public boolean isLive() { return live.get(); }
    private void setLive(boolean live) { this.live.set(live); }

    private ReadOnlyStringWrapper gameNameWrapper() { return gameName; }
    public ReadOnlyStringProperty gameNameProperty() { return gameName.getReadOnlyProperty(); }
    public String getGameName() { return gameName.get(); }
    private void setGameName(String gameName) { this.gameName.set(gameName); }

}
