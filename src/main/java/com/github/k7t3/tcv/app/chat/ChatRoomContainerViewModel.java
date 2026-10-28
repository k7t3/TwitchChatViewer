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

package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.emoji.ChatEmojiStore;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.app.event.UserFilteringEvent;
import com.github.k7t3.tcv.app.reactive.ChatMessageSubscriber;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.FXTaskGroup;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.*;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import com.github.k7t3.tcv.view.chat.ChatFont;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.WorkerStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChatRoomContainerViewModel extends AbstractViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomContainerViewModel.class);

    private final ObservableList<ChatRoomViewModel> chatRoomList = FXCollections.observableArrayList(c -> new Observable[] { c.selectedProperty() });
    private final ObservableList<ChatRoomViewModel> floatingChatRoomList = FXCollections.observableArrayList();
    private final ObservableList<ChatRoomViewModel> allChatRooms = FXCollections.observableArrayList();

    /** 選択しているチャットルーム*/
    private final ObservableList<ChatRoomViewModel> selectedList = new FilteredList<>(chatRoomList, ChatRoomViewModel::isSelected);

    private final ReadOnlyBooleanWrapper selectMode = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper selectingCount = new ReadOnlyIntegerWrapper();
    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper();
    private final Supplier<LoadRequest> loadRequestFactory;
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Object loadLock = new Object();
    private Object loadAttempt;
    private FXTask<Void> activeLoad;
    private ChatResources pendingResources;
    private ChatResources resources;
    private final List<ChatOpeningEvent> pendingChatOpenings = new ArrayList<>();

    /** 読込開始時のFX上で固定したフィルターと背景の取得処理。 */
    public record LoadRequest(ChatFilters filters, Callable<ChatResources> loader) {}

    private GlobalChatBadgeStore globalBadgeStore;
    private ChatEmoteStore chatEmoteStore;
    private final DefinedChatColors definedChatColors = new DefinedChatColors();
    private ChatEmojiStore emojiStore;
    private ChatFilters chatFilters;

    private final IntegerProperty chatCacheSize = new SimpleIntegerProperty();
    private final BooleanProperty showUserName = new SimpleBooleanProperty(true);
    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);
    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>(null);

    ChatRoomContainerViewModel(ChatFilters filters, Callable<ChatResources> loader, Executor executor) {
        this(() -> new LoadRequest(filters, loader), executor, ignored -> {});
    }

    /** 要求はFX上の読込開始時に固定し、取得は借用executorで実行する。結果の実解放を終了所有者へ登録する。 */
    public ChatRoomContainerViewModel(Supplier<LoadRequest> loadRequestFactory, Executor executor,
                                       Consumer<CompletionStage<Void>> shutdownAwaiter) {
        this.loadRequestFactory = Objects.requireNonNull(loadRequestFactory);
        this.executor = Objects.requireNonNull(executor);
        this.shutdownAwaiter = Objects.requireNonNull(shutdownAwaiter);
        selectingCount.bind(Bindings.size(selectedList));

        // 一つ以上選択されているときは選択モード
        selectMode.bind(selectingCount.greaterThan(0));

        // mergeリストに同期させるためのリスナを追加
        chatRoomList.addListener(this::injectionItemListener);
        floatingChatRoomList.addListener(this::injectionItemListener);
    }

    public void bindChatPreferences(ChatPreferences chatPreferences) {
        chatCacheSize.bind(chatPreferences.chatCacheSizeProperty());
        showUserName.bind(chatPreferences.showUserNameProperty());
        showBadges.bind(chatPreferences.showBadgesProperty());
        font.bind(chatPreferences.fontProperty().map(settings -> new ChatFont(settings.family(), settings.size())));
    }

    private void injectionItemListener(ListChangeListener.Change<? extends ChatRoomViewModel> c) {
        while (c.next()) {
            if (c.wasAdded())
                allChatRooms.addAll(c.getAddedSubList());
            if (c.wasRemoved())
                allChatRooms.removeAll(c.getRemoved());
        }
    }

    private <T extends ChatRoomEvent> FlowableSubscriber<ChatRoomEvent> subscriber(SessionSubscriptions subscriptions, Class<T> type, Consumer<T> consumer) {
        return subscriptions.own(new DownCastFXSubscriber<>(type, consumer));
    }

    @Override
    protected void onSubscribeEvents(EventSubscribers eventSubscribers, SessionSubscriptions subscriptions) {
        var clearedSub = subscriber(subscriptions, ChatClearedEvent.class, this::onChatCleared);
        var deleteSub = subscriber(subscriptions, ChatMessageDeletedEvent.class, this::onMessageDeleted);
        var stateSub = subscriber(subscriptions, ChatRoomStateUpdatedEvent.class, this::onStateUpdated);
        var cheeredSub = subscriber(subscriptions, CheeredEvent.class, this::onCheered);
        var raidReceivedSub = subscriber(subscriptions, RaidReceivedEvent.class, this::onRaidReceived);
        var giftedSub = subscriber(subscriptions, UserGiftedSubscribeEvent.class, this::onGiftedSubs);
        var subsSub = subscriber(subscriptions, UserSubscribedEvent.class, this::onSubs);
        var chatSub = subscriptions.own(new ChatMessageSubscriber(this::onChatPosted));

        eventSubscribers.subscribeChatEvent(clearedSub);
        eventSubscribers.subscribeChatEvent(deleteSub);
        eventSubscribers.subscribeChatEvent(stateSub);
        eventSubscribers.subscribeChatEvent(cheeredSub);
        eventSubscribers.subscribeChatEvent(raidReceivedSub);
        eventSubscribers.subscribeChatEvent(giftedSub);
        eventSubscribers.subscribeChatEvent(subsSub);
        eventSubscribers.subscribeMessageEvent(chatSub);

        subscriptions.subscribe(ChatOpeningEvent.class, this::onChatOpened);
        subscriptions.subscribe(KeywordFilteringEvent.class, this::onKeywordFiltered);
        subscriptions.subscribe(UserFilteringEvent.class, this::onUserFiltered);
    }

    private Optional<ChatRoomViewModel> find(TwitchChannel channel) {
        return allChatRooms.stream().filter(r -> r.accept(channel)).findFirst();
    }

    private void onChatPosted(ChatMessageEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> {
            var filter = chatFilters.getFilter();
            var hidden = filter.test(e.getChatData());
            c.onChatAdded(e, hidden);
        });
    }

    private void onKeywordFiltered(KeywordFilteringEvent event) {
        allChatRooms.forEach(c -> c.chatFilter(event));
    }

    private void onUserFiltered(UserFilteringEvent event) {
        allChatRooms.forEach(c -> c.chatFilter(event));
    }

    private void onChatCleared(ChatClearedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.clearChatMessages(chatRoom));
    }

    private void onMessageDeleted(ChatMessageDeletedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        var messageId = e.getDeletedMessageId();
        find(channel).ifPresent(c -> c.deleteChatMessage(messageId));
    }

    private void onStateUpdated(ChatRoomStateUpdatedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onStateUpdated(e));
    }

    private void onCheered(CheeredEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onCheered(e));
    }

    private void onRaidReceived(RaidReceivedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onRaidReceived(e));
    }

    private void onGiftedSubs(UserGiftedSubscribeEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onGiftedSubsEvent(e));
    }

    private void onSubs(UserSubscribedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onUserSubsEvent(e));
    }

    private void onChatOpened(ChatOpeningEvent e) {
        if (!loaded.get()) {
            pendingChatOpenings.add(e);
            return;
        }
        switch (e.getChatOpenType()) {
            case MERGED -> openChatRooms(e.getChannels());
            case SEPARATED -> e.getChannels().forEach(this::openChatRoom);
        }
    }

    public ObservableList<ChatRoomViewModel> getChatRoomList() {
        return chatRoomList;
    }

    /**
     * 初期データの取得を開始して結果を FX スレッドで反映する。FX スレッドから呼び出す。
     * 実行中は同じタスクを返す。UI の準備完了は {@link #loadedProperty()}、失敗は {@link #errorProperty()} で確認する。
     * @return データを取得するタスク
     * @throws IllegalStateException このモデルが終了している場合
     */
    public FXTask<Void> loadAsync() {
        if (isClosed()) throw new IllegalStateException("chat container is closed");
        if (activeLoad != null) return activeLoad;
        if (loaded.get()) return FXTask.empty();
        var request = loadRequestFactory.get();
        loading.set(true);
        error.set(null);
        var token = new Object();
        synchronized (loadLock) {
            loadAttempt = token;
        }
        var drained = new CompletableFuture<Void>();
        var executionClaimed = new AtomicBoolean();
        var task = FXTask.<Void>task(() -> {
            final ChatResources result;
            try { result = request.loader().call(); }
            catch (ChatResourcesLoader.CleanupFailure failure) {
                drained.completeExceptionally(failure);
                throw failure;
            }
            synchronized (loadLock) {
                if (isCurrentLoad(token)) {
                    pendingResources = result;
                    return null;
                }
            }
            try { result.close(); }
            catch (RuntimeException | Error failure) {
                // 取消済みTaskの例外には残らないため、実解放の所有者にも保持する。
                drained.completeExceptionally(failure);
                throw failure;
            }
            return null;
        });
        activeLoad = task;
        task.onDone(() -> adoptResources(token, request.filters()));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> failLoading(token));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, event -> {
            if (isCurrentLoad(token)) resetLoading();
        });
        task.setFinally(() -> {
            if (activeLoad == task) {
                activeLoad = null;
                loading.set(false);
            }
        });
        task.completion().whenComplete((ignored, failure) -> {
            // 未開始の取消とワーカーの開始のどちらかだけが、この実行を引き受ける。
            if (task.isCancelled() && executionClaimed.compareAndSet(false, true)) drained.complete(null);
        });
        try {
            shutdownAwaiter.accept(drained.minimalCompletionStage());
            executor.execute(() -> {
                if (!executionClaimed.compareAndSet(false, true)) return;
                try { task.run(); }
                finally { drained.complete(null); }
            });
        } catch (RuntimeException | Error rejected) {
            LOGGER.error("Failed to start chat resource loading", rejected);
            failLoading(token);
            task.cancel();
            drained.complete(null);
        }
        return task;
    }

    private boolean isCurrentLoad(Object token) {
        synchronized (loadLock) {
            return !isClosed() && loadAttempt == token;
        }
    }

    private void adoptResources(Object token, ChatFilters filters) {
        synchronized (loadLock) {
            if (!isCurrentLoad(token)) return;
            resources = pendingResources;
            pendingResources = null;
        }
        try {
            var result = resources;
            filters.applySnapshot(result.filters());
            if (!isCurrentLoad(token)) return;
            chatFilters = filters;
            globalBadgeStore = result.badgeStore();
            chatEmoteStore = result.emoteStore();
            emojiStore = result.emojiStore();
            loaded.set(true);
        } catch (RuntimeException failure) {
            LOGGER.error("Failed to apply chat resources", failure);
            failLoading(token);
            return;
        }
        var openings = List.copyOf(pendingChatOpenings);
        pendingChatOpenings.clear();
        for (var opening : openings) {
            if (!isCurrentLoad(token)) break;
            onChatOpened(opening);
        }
    }

    private void failLoading(Object token) {
        if (!isCurrentLoad(token)) return;
        resetLoading();
        error.set(Resources.getString("chat.load.failed"));
    }

    private void resetLoading() {
        final ChatResources pending;
        final ChatResources adopted;
        synchronized (loadLock) {
            loadAttempt = null;
            pending = pendingResources;
            adopted = resources;
            pendingResources = null;
            resources = null;
        }
        var task = activeLoad;
        activeLoad = null;
        if (task != null) task.cancel();
        loading.set(false);
        loaded.set(false);
        error.set(null);
        chatFilters = null;
        globalBadgeStore = null;
        chatEmoteStore = null;
        emojiStore = null;
        releaseResources(pending);
        if (adopted != pending) releaseResources(adopted);
    }

    private void releaseResources(ChatResources owned) {
        if (owned == null) return;
        var released = new CompletableFuture<Void>();
        Runnable release = () -> {
            try {
                owned.close();
                released.complete(null);
            } catch (RuntimeException | Error failure) {
                released.completeExceptionally(failure);
                throw failure;
            }
        };
        // 追跡の登録や借用workerの拒否でも実解放を続け、FX上ではI/Oを行わない。
        CloseActions.runAll(
                () -> shutdownAwaiter.accept(released.minimalCompletionStage()),
                () -> {
                    try { executor.execute(release); }
                    catch (RuntimeException | Error rejected) {
                        LOGGER.warn("Chat resource release worker is unavailable", rejected);
                        try {
                            Thread.ofVirtual().name("TCV-Chat-Resources-Close").start(() -> {
                                try { release.run(); }
                                catch (RuntimeException | Error failure) {
                                    LOGGER.error("Failed to release chat resources on fallback worker", failure);
                                }
                            });
                        } catch (RuntimeException | Error fallbackFailure) {
                            if (fallbackFailure != rejected) rejected.addSuppressed(fallbackFailure);
                            released.completeExceptionally(rejected);
                            throw rejected;
                        }
                    }
                });
    }

    private void openChatRooms(List<TwitchChannelViewModel> channels) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        // すでに登録済みのチャンネルは除外する
        var filtered = channels.stream()
                .filter(c -> chatRoomList.stream().noneMatch(c2 -> c2.accept(c.getChannel())))
                .filter(c -> floatingChatRoomList.stream().noneMatch(c2 -> c2.accept(c.getChannel())))
                .toList();

        if (filtered.isEmpty()) {
            return;
        }

        // 対象のチャンネルが一つのときは通常の登録フロー
        if (filtered.size() == 1) {
            openChatRoom(filtered.getFirst());
            return;
        }

        var chatRooms = filtered.stream().map(c -> {
            var chatRoom = new SingleChatRoomViewModel(
                    this,
                    globalBadgeStore,
                    chatEmoteStore,
                    definedChatColors,
                    emojiStore,
                    c,
                    chatFilters
            );
            bindChatRoomProperties(chatRoom);
            return chatRoom;
        }).toList();

        var merged = new MergedChatRoomViewModel(
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                chatRooms,
                emojiStore,
                this,
                chatFilters,
                chatCacheSize.get()
        );

        chatRooms.forEach(SingleChatRoomViewModel::joinChatAsync);

        bindChatRoomProperties(merged);
        chatRoomList.add(merged);
    }

    private void openChatRoom(TwitchChannelViewModel channel) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        var exist = chatRoomList.stream()
                .filter(vm -> vm.accept(channel.getChannel()))
                .findFirst();
        if (exist.isPresent()) {
            return;
        }

        exist = floatingChatRoomList.stream()
                .filter(vm -> vm.accept(channel.getChannel()))
                .findFirst();
        if (exist.isPresent()) {
            return;
        }

        var chatRoomViewModel = new SingleChatRoomViewModel(
                this,
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                emojiStore,
                channel,
                chatFilters
        );

        bindChatRoomProperties(chatRoomViewModel);
        chatRoomList.add(chatRoomViewModel);
    }

    private void bindChatRoomProperties(ChatRoomViewModel viewModel) {
        viewModel.chatCacheSizeProperty().bind(chatCacheSize);
        viewModel.showNameProperty().bind(showUserName);
        viewModel.showBadgesProperty().bind(showBadges);
        viewModel.fontProperty().bind(font);
        viewModel.selectModeProperty().bind(selectMode);
    }

    /**
     * 管理しているChatリストから削除する。
     * チャットから切断することはしない。切断済みのものを渡すこと。
     */
    void onLeft(ChatRoomViewModel chat) {
        chatRoomList.removeIf(vm -> vm.equals(chat));
        floatingChatRoomList.removeIf(vm -> vm.equals(chat));
    }

    public ObservableList<ChatRoomViewModel> getSelectedList() {
        return selectedList;
    }

    public ObservableList<ChatRoomViewModel> getFloatingChatRoomList() {
        return floatingChatRoomList;
    }

    public void removeLast() {
        var chatRoomList = getChatRoomList();
        if (chatRoomList.isEmpty()) return;

        var last = getChatRoomList().getLast();
        chatRoomList.remove(last);
        leaveRooms(List.of(last));
    }

    void popOutAsFloatableStage(ChatRoomViewModel chatRoom) {
        chatRoomList.remove(chatRoom);
        floatingChatRoomList.add(chatRoom);
    }

    void restoreToContainer(ChatRoomViewModel chatRoom) {
        if (floatingChatRoomList.remove(chatRoom)) {
            chatRoomList.add(chatRoom);
        }
    }

    public void selectAll() {
        chatRoomList.forEach(c -> c.setSelected(true));
    }

    public void unselectAll() {
        chatRoomList.forEach(c -> c.setSelected(false));
    }

    /**
     * 選択しているすべてのチャットルームを閉じる。
     * <p>
     *     チャットルームから退出し、そのチャンネルが
     *     持続化されていなければ({@link TwitchChannelViewModel#persistentProperty()})
     *     ロードしていたチャンネルが開放される。
     * </p>
     */
    public void closeAll() {
        var selectedList = new ArrayList<>(getSelectedList());
        chatRoomList.removeAll(selectedList);
        leaveRooms(selectedList);
    }

    private void leaveRooms(List<ChatRoomViewModel> rooms) {
        if (!rooms.isEmpty()) trackExit(FXTaskGroup.startAll(rooms, ChatRoomViewModel::leaveChatAsync));
    }

    /** 取消可能な UI の task と分離して、全子タスクの終端と失敗をアプリ終了まで所有する。 */
    void trackExit(FXTaskGroup.Batch exits) {
        shutdownAwaiter.accept(exits.settled());
        exits.settled().whenComplete((ignored, failure) -> {
            if (failure != null) LOGGER.error("Failed to leave chat rooms", failure);
        });
    }

    /**
     * 選択しているチャットルームをまとめて一つのチャットルームビューにする。
     * <p>
     *     選択しているチャンネルに{@link MergedChatRoomViewModel}があれば
     *     そのうちの一つを代表として、他のすべてのチャットルームを集約する。
     * </p>
     * <p>
     *     {@link MergedChatRoomViewModel}がない場合はインスタンスを生成し、
     *     そこにチャットルームを集約する。
     * </p>
     */
    public void mergeSelectedChats() {

        var chatRooms = getSelectedList().stream()
                .filter(vm -> vm instanceof SingleChatRoomViewModel)
                .map(vm -> (SingleChatRoomViewModel) vm)
                .toList();

        var mergedChatRooms = getSelectedList().stream()
                .filter(vm -> vm instanceof MergedChatRoomViewModel)
                .map(vm -> (MergedChatRoomViewModel) vm)
                .toList();

        unselectAll();

        if (!mergedChatRooms.isEmpty()) {

            // MergedChatRoomViewModelが存在するときは
            // 先頭の要素を代表としてそれにすべて集約する。
            MergedChatRoomViewModel mergedChatRoom = null;

            for (var m : mergedChatRooms) {
                if (mergedChatRoom == null) {
                    mergedChatRoom = m;
                } else {
                    mergedChatRoom.aggregate(m);
                    chatRoomList.remove(m);
                }
            }

            for (var c : chatRooms) {
                mergedChatRoom.addChatRoom(c);
                chatRoomList.remove(c);
            }

            return;
        }

        var chatRoomViewModel = new MergedChatRoomViewModel(
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                chatRooms,
                emojiStore,
                this,
                chatFilters,
                chatCacheSize.get()
        );

        bindChatRoomProperties(chatRoomViewModel);

        chatRoomList.removeAll(chatRooms);
        chatRoomList.add(chatRoomViewModel);
    }

    @Override
    protected void onLoggedOut() {
        releaseChatRooms();
    }

    private void releaseChatRooms() {
        pendingChatOpenings.clear();
        var chatRooms = new ArrayList<>(chatRoomList);
        chatRooms.addAll(floatingChatRoomList);
        CloseActions.runAll(chatRoomList::clear, floatingChatRoomList::clear,
                this::resetLoading, () -> leaveRooms(chatRooms));
    }

    /**
     * チャットの退出を開始する。完了待ちはアプリケーションのワーカー終了処理が行う。
     */
    @Override
    protected void onClose() {
        CloseActions.runAll(this::releaseChatRooms,
                chatCacheSize::unbind, showUserName::unbind, showBadges::unbind, font::unbind);
    }

    // ******************** PROPERTIES ********************

    /** すべてのリソースとフィルターを UI に反映し、チャットを開ける状態。 */
    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }

    /** キューでの待機を含む初期ロード中の状態。 */
    public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    public boolean isLoading() { return loading.get(); }

    public ReadOnlyStringProperty errorProperty() { return error.getReadOnlyProperty(); }
    public String getError() { return error.get(); }

    public ReadOnlyBooleanProperty selectModeProperty() { return selectMode.getReadOnlyProperty(); }
    public boolean isSelectMode() { return selectMode.get(); }

    public ReadOnlyIntegerProperty selectingCountProperty() { return selectingCount.getReadOnlyProperty(); }
    public int getSelectingCount() { return selectingCount.get(); }
}
