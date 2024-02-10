package com.github.k7t3.tcv.vm.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelCollections;
import com.github.k7t3.tcv.vm.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.core.ExceptionHandler;
import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SearchChannelViewModel implements ViewModel, SceneLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchChannelViewModel.class);

    private static final int DELAY_SECONDS = 2;

    private final StringProperty keyword = new SimpleStringProperty();

    private final BooleanProperty onlyLive = new SimpleBooleanProperty(true);

    private final ObservableList<FoundChannelViewModel> channels = FXCollections.observableArrayList();

    private final SearchChannelService searchService = new SearchChannelService();

    private ChatContainerViewModel chatContainerViewModel;

    private Twitch twitch;

    public SearchChannelViewModel() {
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

    public void setChatContainerViewModel(ChatContainerViewModel chatContainerViewModel) {
        this.chatContainerViewModel = chatContainerViewModel;
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
    public void onViewAdded() {
        twitch = AppHelper.getInstance().getTwitch();
    }

    @Override
    public void onViewRemoved() {
        searchService.cancel();
        searchService.close();
    }

    private class SearchChannelService extends Service<List<FoundChannelViewModel>> implements Closeable {

        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        public SearchChannelService() {
            setExecutor(executor);
        }

        @Override
        public void close() {
            executor.close();
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
            return new Task<>() {
                @Override
                protected List<FoundChannelViewModel> call() {
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return List.of();
                    }

                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        return List.of();
                    }

                    if (isCancelled()) {
                        return List.of();
                    }

                    LOGGER.info("search channels keyword={}, liveOnly={}", keyword, onlyLive);

                    var channels = new ChannelCollections(twitch);
                    return channels.search(keyword, onlyLive).stream()
                            .map(c -> new FoundChannelViewModel(chatContainerViewModel, c))
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
