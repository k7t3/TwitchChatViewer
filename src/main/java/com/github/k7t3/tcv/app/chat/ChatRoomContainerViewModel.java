package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatMessageFilter;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.event.EventBus;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.prefs.ChatMessageFilterPreferences;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.view.chat.ChatFont;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatRoomContainerViewModel implements ViewModel {

    /** 開かれているチャットルーム(チャンネル)のリスト*/
    private final ObservableList<ChatRoomViewModel> chatRoomList = FXCollections.observableArrayList(c -> new Observable[] { c.selectedProperty() });

    /** フロートモード*/
    private final ObservableList<ChatRoomViewModel> floatingChatRoomList = FXCollections.observableArrayList();

    /** 選択しているチャットルーム*/
    private final ObservableList<ChatRoomViewModel> selectedList = new FilteredList<>(chatRoomList, ChatRoomViewModel::isSelected);

    /** 選択モード*/
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

    private final List<ChatRoomListener> defaultChatRoomListeners;

    public ChatRoomContainerViewModel(
            PostedClipRepository clipRepository,
            ChatPreferences chatPrefs,
            ChatMessageFilterPreferences msgFilterPrefs
    ) {
        var eventBus = EventBus.getInstance();
        eventBus.subscribe(LogoutEvent.class, e -> clearAll());
        eventBus.subscribe(ChatOpeningEvent.class, this::onChatOpened);

        selectingCount.bind(Bindings.size(selectedList));

        // 一つ以上選択されているときは選択モード
        selectMode.bind(selectingCount.greaterThan(0));

        defaultChatRoomListeners = List.of(clipRepository);

        // Preferencesと同期
        chatCacheSize.bind(chatPrefs.chatCacheSizeProperty());
        showUserName.bind(chatPrefs.showUserNameProperty());
        showBadges.bind(chatPrefs.showBadgesProperty());
        font.bind(chatPrefs.fontProperty());
        chatMessageFilter.bind(msgFilterPrefs.chatMessageFilterProperty());
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

        TaskWorker.getInstance().submit(task);

        return task;
    }

    private void openChatRooms(List<TwitchChannelViewModel> channels) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        // すでに登録済みのチャンネルは除外する
        var filtered = channels.stream()
                .filter(c -> chatRoomList.stream().noneMatch(c2 -> c2.hasChannel(c)))
                .filter(c -> floatingChatRoomList.stream().noneMatch(c2 -> c2.hasChannel(c)))
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
            var chatRoom = new SingleChatRoomViewModel(this, globalBadgeStore, chatEmoteStore, definedChatColors, c);
            chatRoom.getChannel().getChatRoomListeners().addAll(defaultChatRoomListeners);
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

        var exist = chatRoomList.stream().filter(vm -> vm.hasChannel(channel)).findFirst();
        if (exist.isPresent()) {
            return;
        }

        exist = floatingChatRoomList.stream().filter(vm -> vm.hasChannel(channel)).findFirst();
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

        var channelViewModel = chatRoomViewModel.getChannel();
        channelViewModel.getChatRoomListeners().addAll(defaultChatRoomListeners);

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

    public void closeAll() {
        var selectedList = new ArrayList<>(getSelectedList());
        selectedList.forEach(ChatRoomViewModel::leaveChatAsync);
    }

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

    /**
     * コンテナをクリアする。
     * チャットがすべてログアウトされるまでスレッドをブロックする。
     */
    public void clearAll() {
        var singles = getChatRoomList();
        var floatings = getFloatingChatRoomList();

        var chatRooms = new ArrayList<ChatRoomViewModel>();
        chatRooms.addAll(singles);
        chatRooms.addAll(floatings);

        singles.clear();
        floatings.clear();

        chatRooms.stream().map(ChatRoomViewModel::leaveChatAsync).forEach(task -> {
            try {
                task.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                // no-op
            }
        });
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyBooleanWrapper loadedWrapper() { return loaded; }
    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }
    private void setLoaded(boolean loaded) { this.loaded.set(loaded); }

    public ReadOnlyBooleanProperty selectModeProperty() { return selectMode.getReadOnlyProperty(); }
    public boolean isSelectMode() { return selectMode.get(); }

    public ReadOnlyIntegerProperty selectingCountProperty() { return selectingCount.getReadOnlyProperty(); }
    public int getSelectingCount() { return selectingCount.get(); }
}
