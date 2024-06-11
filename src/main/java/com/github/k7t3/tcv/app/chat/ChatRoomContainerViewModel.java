package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatMessageFilter;
import com.github.k7t3.tcv.app.core.AbstractViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.reactive.ChatMessageSubscriber;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.*;
import com.github.k7t3.tcv.prefs.ChatMessageFilterPreferences;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ChatRoomContainerViewModel extends AbstractViewModel {

    private final ObservableList<ChatRoomViewModel> chatRoomList = FXCollections.observableArrayList(c -> new Observable[] { c.selectedProperty() });
    private final ObservableList<ChatRoomViewModel> floatingChatRoomList = FXCollections.observableArrayList();
    private final ObservableList<ChatRoomViewModel> allChatRooms = FXCollections.observableArrayList();

    /** 選択しているチャットルーム*/
    private final ObservableList<ChatRoomViewModel> selectedList = new FilteredList<>(chatRoomList, ChatRoomViewModel::isSelected);

    private final ReadOnlyBooleanWrapper selectMode = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper selectingCount = new ReadOnlyIntegerWrapper();
    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private GlobalChatBadgeStore globalBadgeStore;
    private ChatEmoteStore chatEmoteStore;
    private final DefinedChatColors definedChatColors = new DefinedChatColors();

    private final IntegerProperty chatCacheSize = new SimpleIntegerProperty();
    private final BooleanProperty showUserName = new SimpleBooleanProperty(true);
    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);
    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>(null);
    private final ObjectProperty<ChatMessageFilter> chatMessageFilter = new SimpleObjectProperty<>(ChatMessageFilter.DEFAULT);

    private final List<FlowableSubscriber<?>> subscribers = new ArrayList<>();

    public ChatRoomContainerViewModel() {
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
        font.bind(chatPreferences.fontProperty());
    }

    public void bindChatMessageFilterPreferences(ChatMessageFilterPreferences filterPreferences) {
        chatMessageFilter.bind(filterPreferences.chatMessageFilterProperty());
    }

    private void injectionItemListener(ListChangeListener.Change<? extends ChatRoomViewModel> c) {
        while (c.next()) {
            if (c.wasAdded())
                allChatRooms.addAll(c.getAddedSubList());
            if (c.wasRemoved())
                allChatRooms.removeAll(c.getRemoved());
        }
    }

    private <T extends ChatRoomEvent> FlowableSubscriber<ChatRoomEvent> subscriber(Class<T> type, Consumer<T> consumer) {
        return new DownCastFXSubscriber<>(type, consumer);
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        var clearedSub = subscriber(ChatClearedEvent.class, this::onChatCleared);
        var deleteSub = subscriber(ChatMessageDeletedEvent.class, this::onMessageDeleted);
        var stateSub = subscriber(ChatRoomStateUpdatedEvent.class, this::onStateUpdated);
        var cheeredSub = subscriber(CheeredEvent.class, this::onCheered);
        var raidReceivedSub = subscriber(RaidReceivedEvent.class, this::onRaidReceived);
        var giftedSub = subscriber(UserGiftedSubscribeEvent.class, this::onGiftedSubs);
        var subsSub = subscriber(UserSubscribedEvent.class, this::onSubs);
        var chatSub = new ChatMessageSubscriber(this::onChatPosted);

        eventSubscribers.subscribeChatEvent(clearedSub);
        eventSubscribers.subscribeChatEvent(deleteSub);
        eventSubscribers.subscribeChatEvent(stateSub);
        eventSubscribers.subscribeChatEvent(cheeredSub);
        eventSubscribers.subscribeChatEvent(raidReceivedSub);
        eventSubscribers.subscribeChatEvent(giftedSub);
        eventSubscribers.subscribeChatEvent(subsSub);
        eventSubscribers.subscribeMessageEvent(chatSub);

        subscribers.addAll(List.of(
                clearedSub,
                deleteSub,
                stateSub,
                cheeredSub,
                raidReceivedSub,
                giftedSub,
                subsSub,
                chatSub
        ));

        // チャットを開くイベント
        subscribe(ChatOpeningEvent.class, this::onChatOpened);
    }

    private Optional<ChatRoomViewModel> find(TwitchChannel channel) {
        return allChatRooms.stream().filter(r -> r.accept(channel)).findFirst();
    }

    private void onChatPosted(ChatMessageEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = chatRoom.getChannel();
        find(channel).ifPresent(c -> c.onChatAdded(e));
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
        switch (e.getChatOpenType()) {
            case MERGED -> openChatRooms(e.getChannels());
            case SEPARATED -> e.getChannels().forEach(this::openChatRoom);
        }
    }

    public ObservableList<ChatRoomViewModel> getChatRoomList() {
        return chatRoomList;
    }

    public FXTask<Void> loadAsync() {
        if (loaded.get()) return FXTask.empty();
        loaded.set(true);

        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        var task = FXTask.task(() -> {
            var globalBadges = new GlobalChatBadges();
            globalBadges.load(twitch);
            globalBadgeStore = new GlobalChatBadgeStore(globalBadges);
            chatEmoteStore = new ChatEmoteStore();
        });

        task.runAsync();

        return task;
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
                    c
            );
            bindChatRoomProperties(chatRoom);
            return chatRoom;
        }).toList();

        var merged = new MergedChatRoomViewModel(
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                chatRooms,
                this
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
                channel
        );

        bindChatRoomProperties(chatRoomViewModel);
        chatRoomList.add(chatRoomViewModel);
    }

    private void bindChatRoomProperties(ChatRoomViewModel viewModel) {
        viewModel.chatCacheSizeProperty().bind(chatCacheSize);
        viewModel.showNameProperty().bind(showUserName);
        viewModel.showBadgesProperty().bind(showBadges);
        viewModel.fontProperty().bind(font);
        viewModel.chatMessageFilterProperty().bind(chatMessageFilter);
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
        last.leaveChatAsync();
        chatRoomList.remove(last);
    }

    public void popOutAsFloatableStage(ChatRoomViewModel chatRoom) {
        chatRoomList.remove(chatRoom);
        floatingChatRoomList.add(chatRoom);
    }

    public void restoreToContainer(ChatRoomViewModel chatRoom) {
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
        selectedList.forEach(ChatRoomViewModel::leaveChatAsync);
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
                this
        );

        bindChatRoomProperties(chatRoomViewModel);

        chatRoomList.removeAll(chatRooms);
        chatRoomList.add(chatRoomViewModel);
    }

    @Override
    public void onLogout() {
        subscribers.forEach(FlowableSubscriber::cancel);
        subscribers.clear();

        var singles = getChatRoomList();
        var floatings = getFloatingChatRoomList();

        var chatRooms = new ArrayList<ChatRoomViewModel>();
        chatRooms.addAll(singles);
        chatRooms.addAll(floatings);

        singles.clear();
        floatings.clear();

        chatRooms.forEach(ChatRoomViewModel::leaveChatAsync);
    }

    /**
     * コンテナをクリアする。
     * チャットがすべてログアウトされるまでスレッドをブロックする。
     */
    @Override
    public void close() {
        subscribers.forEach(FlowableSubscriber::cancel);

        var singles = getChatRoomList();
        var floatings = getFloatingChatRoomList();

        var chatRooms = new ArrayList<ChatRoomViewModel>();
        chatRooms.addAll(singles);
        chatRooms.addAll(floatings);
        chatRooms.stream().map(ChatRoomViewModel::leaveChatAsync).forEach(task -> {
            try {
                task.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                // no-op
            }
        });
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }

    public ReadOnlyBooleanProperty selectModeProperty() { return selectMode.getReadOnlyProperty(); }
    public boolean isSelectMode() { return selectMode.get(); }

    public ReadOnlyIntegerProperty selectingCountProperty() { return selectingCount.getReadOnlyProperty(); }
    public int getSelectingCount() { return selectingCount.get(); }
}
