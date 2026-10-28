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
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelFinder;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SearchChannelViewModel extends AbstractViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchChannelViewModel.class);

    private static final int DELAY_SECONDS = 2;

    private final StringProperty keyword = new SimpleStringProperty();
    private final BooleanProperty onlyLive = new SimpleBooleanProperty(true);

    private final ObservableList<FoundChannelViewModel> channels = FXCollections.observableArrayList();

    private final SearchChannelService searchService = new SearchChannelService();
    private final ObjectProperty<Twitch> twitch = new SimpleObjectProperty<>();
    private final ChannelViewModelRepository channelRepository;
    private final ImageLoader images;
    private final Consumer<Throwable> failureHandler;
    private boolean loggedOut;
    private final ChangeListener<Twitch> sessionChanged = (observable, previous, next) -> {
        loggedOut = next == null;
        cancelSearch();
        try { clearChannels(); }
        catch (RuntimeException | Error failure) { reportFailure(failure); }
    };

    /** executor と画像サービスは借用し、アプリの所有者が終了する。 */
    public SearchChannelViewModel(ChannelViewModelRepository channelRepository, ImageLoader images, Executor executor,
                           Consumer<Throwable> failureHandler) {
        this.channelRepository = channelRepository;
        this.images = images;
        this.failureHandler = Objects.requireNonNull(failureHandler);
        searchService.setExecutor(Objects.requireNonNull(executor));
        twitch.addListener(sessionChanged);
        init();
    }

    private void init() {
        searchService.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED,
                e -> { if (isCurrent(searchService.request)) reportFailure(searchService.getException()); });
        searchService.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                e -> applyResults());
    }

    public void search() {
        search(TimeUnit.SECONDS, DELAY_SECONDS);
    }

    public void search(TimeUnit unit, long amount) {
        if (isClosed()) throw new IllegalStateException("search view model is closed");
        if (amount < 0) throw new IllegalArgumentException("negative search delay");
        cancelSearch();
        try {
            clearChannels();
            if (isClosed() || loggedOut || getTwitch() == null || getKeyword() == null || getKeyword().isBlank()) return;
            searchService.search(unit.toMillis(amount), getKeyword(), isOnlyLive());
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(this::cancelSearch, () -> reportFailure(failure));
        }
    }

    public ObservableList<FoundChannelViewModel> getChannels() {
        return channels;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return searchService.runningProperty();
    }

    public boolean isLoading() {
        return searchService.isRunning();
    }

    private record SearchRequest(Twitch twitch, String keyword, boolean onlyLive, long delayMillis) {
    }

    @Override
    protected void onClose() {
        CloseActions.runAll(this::onLoggedOut, () -> {
            twitch.removeListener(sessionChanged);
            twitch.unbind();
            twitch.set(null);
        });
    }

    @Override
    protected void onLoggedOut() {
        loggedOut = true;
        CloseActions.runAll(this::cancelSearch, this::clearChannels);
    }

    private void clearChannels() {
        var previous = List.copyOf(channels);
        channels.clear();
        closeResults(previous);
    }

    private void cancelSearch() {
        searchService.request = null;
        searchService.cancel();
        // Task が捕捉した旧 Twitch と結果も、画面の寿命から切り離す。
        searchService.reset();
    }

    private boolean isCurrent(SearchRequest request) {
        return request != null && request == searchService.request && !isClosed() && !loggedOut && request.twitch() == getTwitch();
    }

    private void applyResults() {
        var request = searchService.request;
        if (!isCurrent(request)) return;
        var created = new ArrayList<FoundChannelViewModel>();
        try {
            for (var channel : searchService.getValue()) {
                if (!isCurrent(request)) break;
                created.add(new FoundChannelViewModel(channelRepository, channel, images, failureHandler,
                        new BrowserActions(searchService.getExecutor(), channelRepository::trackChatOperation, failureHandler)));
            }
            if (!isCurrent(request)) { closeResults(created); return; }
            channels.setAll(created);
        } catch (RuntimeException | Error failure) {
            try { CloseActions.runAll(() -> rethrow(failure), () -> closeResults(created)); }
            catch (RuntimeException | Error accumulated) { reportFailure(accumulated); }
        }
    }

    private static void closeResults(List<FoundChannelViewModel> results) {
        CloseActions.runAll(results.stream().map(model -> (Runnable) model::close).toArray(Runnable[]::new));
    }

    private void reportFailure(Throwable failure) {
        if (isClosed()) LOGGER.error("search result cleanup failed", failure);
        else failureHandler.accept(failure);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }

    public ObjectProperty<Twitch> twitchProperty() { return twitch; }
    public Twitch getTwitch() { return twitch.get(); }
    public void setTwitch(Twitch twitch) { this.twitch.set(twitch); }

    private class SearchChannelService extends Service<List<FoundChannel>> {

        private SearchRequest request;

        public void search(long delayMillis, String keyword, boolean live) {
            this.request = new SearchRequest(getTwitch(), keyword, live, delayMillis);
            restart();
        }

        @Override
        protected Task<List<FoundChannel>> createTask() {
            final var request = this.request;
            return new Task<>() {
                @Override
                protected List<FoundChannel> call() {
                    if (request.twitch() == null) {
                        return List.of();
                    }
                    if (request.keyword() == null || request.keyword().trim().isEmpty()) {
                        return List.of();
                    }

                    try {
                        // APIの過度なコールを避けるために検索を即座に
                        // 行わず遅延時間を挟んでから検索する。
                        Thread.sleep(request.delayMillis());
                    } catch (InterruptedException e) {
                        return List.of();
                    }

                    if (isCancelled()) {
                        return List.of();
                    }

                    LOGGER.info("search channels keyword={}, liveOnly={}", request.keyword(), request.onlyLive());

                    var finder = new ChannelFinder(request.twitch());
                    return finder.search(request.keyword(), request.onlyLive());
                }
            };
        }
    }

    // ******************** PROPERTIES ********************

    public StringProperty keywordProperty() { return keyword; }
    public String getKeyword() { return keyword.get(); }
    public void setKeyword(String keyword) { this.keyword.set(keyword); }

    public BooleanProperty onlyLiveProperty() { return onlyLive; }
    public boolean isOnlyLive() { return onlyLive.get(); }
    public void setOnlyLive(boolean onlyLive) { this.onlyLive.set(onlyLive); }

}
