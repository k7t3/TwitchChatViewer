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
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.property.*;
import javafx.scene.image.Image;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

public class FoundChannelViewModel extends AbstractViewModel {

    private static final String CHANNEL_URL_FORMAT = "https://www.twitch.tv/%s";

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private final ReadOnlyObjectWrapper<Broadcaster> broadcaster;
    private final ReadOnlyObjectWrapper<Image> profileImage;
    private final ReadOnlyBooleanWrapper live;
    private final ReadOnlyStringWrapper gameName;
    private final ChannelViewModelRepository channelRepository;
    private final ImageLoader images;
    private LazyImage profileLoad;
    private final Consumer<Throwable> failures;
    private final BrowserActions browsers;
    private Opening opening;
    private long revision;
    private boolean loggedOut;

    FoundChannelViewModel(ChannelViewModelRepository channelRepository, FoundChannel channel, ImageLoader images,
                          Consumer<Throwable> failures, BrowserActions browsers) {
        this.failures = failures;
        this.browsers = browsers;
        broadcaster = new ReadOnlyObjectWrapper<>();
        profileImage = new ReadOnlyObjectWrapper<>();
        live = new ReadOnlyBooleanWrapper();
        gameName = new ReadOnlyStringWrapper();
        this.channelRepository = channelRepository;
        this.images = images;
        try { update(channel); }
        catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, this::close);
        }
    }

    public void update(FoundChannel channel) {
        checkActive();
        var token = ++revision;
        cancelOpening();
        releaseProfileImage();
        if (!isCurrent(token)) return;
        var loaded = images.load(channel.getBroadcaster().getProfileImageUrl(), PROFILE_IMAGE_WIDTH, PROFILE_IMAGE_HEIGHT);
        if (!isCurrent(token)) { loaded.close(); return; }
        profileLoad = loaded;
        setBroadcaster(channel.getBroadcaster());
        if (!isCurrent(token)) return;
        profileImage.bind(loaded.valueProperty());
        if (!isCurrent(token)) return;
        setGameName(channel.getGameName());
        if (isCurrent(token)) setLive(channel.isLive());
    }

    private void releaseProfileImage() {
        var owned = profileLoad;
        profileLoad = null;
        CloseActions.runAll(() -> { profileImage.unbind(); profileImage.set(null); },
                () -> { if (owned != null) owned.close(); });
    }

    private boolean isCurrent(long token) { return !isClosed() && !loggedOut && revision == token; }

    private void checkActive() {
        if (isClosed() || loggedOut) throw new IllegalStateException("search result is no longer active");
    }

    private void cancelOpening() {
        var owned = opening;
        opening = null;
        if (owned != null) owned.result.cancel(true);
    }

    private void endScope() {
        loggedOut = true;
        revision++;
        CloseActions.runAll(this::cancelOpening, this::releaseProfileImage, browsers::cancelAll,
                () -> { if (isClosed()) browsers.close(); });
    }

    @Override protected void onLoggedOut() { endScope(); }
    @Override protected void onClose() { endScope(); }

    public FXTask<Boolean> openChannelPageOnBrowser() {
        checkActive();
        return browsers.open(CHANNEL_URL_FORMAT.formatted(getBroadcaster().getUserLogin()));
    }

    /** 共有取得の観測だけを所有する。他の検索結果・画面の取得は取り消さない。 */
    public FXTask<TwitchChannelViewModel> joinChatAsync() {
        checkActive();
        if (opening != null && !opening.result.isDone()) return opening.result;
        var request = new Opening();
        opening = request;
        request.result.onFailure(failure -> { if (request.isCurrent()) failures.accept(failure); });
        request.result.setFinally(() -> { if (opening == request) opening = null; });
        try {
            var source = channelRepository.getChannelAsync(getBroadcaster());
            request.result.cancelWhenCancelled(source);
            source.onDone(value -> { request.value = value; request.result.run(); });
            source.onFailure(failure -> { request.failure = failure; request.result.run(); });
        } catch (RuntimeException | Error failure) {
            request.failure = failure;
            request.result.run();
        }
        return request.result;
    }

    private final class Opening {
        final long token = revision;
        TwitchChannelViewModel value;
        Throwable failure;
        final FXTask<TwitchChannelViewModel> result = FXTask.observation(() -> {
            if (!isCurrent()) throw new CancellationException("search result opening was retired");
            if (failure instanceof Error error) throw error;
            if (failure instanceof Exception exception) throw exception;
            publish(new ChatOpeningEvent(value));
            return value;
        });
        boolean isCurrent() { return FoundChannelViewModel.this.isCurrent(token) && opening == this && !result.isCancelled(); }
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyObjectWrapper<Broadcaster> broadcasterWrapper() { return broadcaster; }
    public ReadOnlyObjectProperty<Broadcaster> broadcasterProperty() { return broadcaster.getReadOnlyProperty(); }
    public Broadcaster getBroadcaster() { return broadcaster.get(); }
    private void setBroadcaster(Broadcaster broadcaster) { this.broadcaster.set(broadcaster); }

    private ReadOnlyObjectWrapper<Image> profileImageWrapper() { return profileImage; }
    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImage.getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImage.get(); }

    private ReadOnlyBooleanWrapper liveWrapper() { return live; }
    public ReadOnlyBooleanProperty liveProperty() { return live.getReadOnlyProperty(); }
    public boolean isLive() { return live.get(); }
    private void setLive(boolean live) { this.live.set(live); }

    private ReadOnlyStringWrapper gameNameWrapper() { return gameName; }
    public ReadOnlyStringProperty gameNameProperty() { return gameName.getReadOnlyProperty(); }
    public String getGameName() { return gameName.get(); }
    private void setGameName(String gameName) { this.gameName.set(gameName); }

}
