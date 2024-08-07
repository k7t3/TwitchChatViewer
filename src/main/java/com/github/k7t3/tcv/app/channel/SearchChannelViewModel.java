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
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelFinder;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SearchChannelViewModel extends AbstractViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchChannelViewModel.class);

    private static final int DELAY_SECONDS = 2;

    private final StringProperty keyword = new SimpleStringProperty();
    private final BooleanProperty onlyLive = new SimpleBooleanProperty(true);

    private final ObservableList<FoundChannelViewModel> channels = FXCollections.observableArrayList();

    private final SearchChannelService searchService = new SearchChannelService();
    private final ObjectProperty<Twitch> twitch = new SimpleObjectProperty<>();
    private final ChannelViewModelRepository channelRepository;

    public SearchChannelViewModel(ChannelViewModelRepository channelRepository) {
        this.channelRepository = channelRepository;
        init();
    }

    private void init() {
        searchService.addEventHandler(WorkerStateEvent.WORKER_STATE_SCHEDULED,
                e -> channels.clear());
        searchService.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED,
                e -> ExceptionHandler.handle(searchService.getException()));
        searchService.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                e -> channels.setAll(searchService.getValue()));
    }

    public void search() {
        search(TimeUnit.SECONDS, DELAY_SECONDS);
    }

    public void search(TimeUnit unit, long amount) {
        searchService.search(unit.toMillis(amount), getKeyword(), isOnlyLive());
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

    public ObjectProperty<Twitch> twitchProperty() { return twitch; }
    public Twitch getTwitch() { return twitch.get(); }
    public void setTwitch(Twitch twitch) { this.twitch.set(twitch); }

    private class SearchChannelService extends Service<List<FoundChannelViewModel>> {

        public SearchChannelService() {
        }

        private String keyword;
        private boolean onlyLive;
        private long delayMillis;

        public void search(long delayMillis, String keyword, boolean live) {
            this.delayMillis = delayMillis;
            this.keyword = keyword;
            this.onlyLive = live;
            restart();
        }

        @Override
        protected Task<List<FoundChannelViewModel>> createTask() {
            final var twitch = getTwitch();
            return new Task<>() {
                @Override
                protected List<FoundChannelViewModel> call() {
                    if (twitch == null) {
                        return List.of();
                    }
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return List.of();
                    }

                    try {
                        // APIの過度なコールを避けるために検索を即座に
                        // 行わず遅延時間を挟んでから検索する。
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        return List.of();
                    }

                    if (isCancelled()) {
                        return List.of();
                    }

                    LOGGER.info("search channels keyword={}, liveOnly={}", keyword, onlyLive);

                    var finder = new ChannelFinder(twitch);
                    return finder.search(keyword, onlyLive)
                            .stream()
                            .map(c -> new FoundChannelViewModel(channelRepository, c))
                            .toList();
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
