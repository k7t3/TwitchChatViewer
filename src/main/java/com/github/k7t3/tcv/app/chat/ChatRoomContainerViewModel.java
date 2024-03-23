package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatFont;
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

    private final BooleanProperty showUserName = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>(null);

    private final ObjectProperty<ChatMessageFilter> chatMessageFilter = new SimpleObjectProperty<>(ChatMessageFilter.DEFAULT);

    private List<ChatRoomListener> defaultChatRoomListeners;

    public ChatRoomContainerViewModel() {
        initialize();
    }

    private void initialize() {
        var helper = AppHelper.getInstance();

        helper.authorizedProperty().addListener((ob, o, n) -> {
            if (!n) {
                clearAll();
            }
        });

        selectingCount.bind(Bindings.size(selectedList));

        // 一つ以上選択されているときは選択モード
        selectMode.bind(selectingCount.greaterThan(0));

        defaultChatRoomListeners = List.of(helper.getClipRepository());

        // Preferencesと同期
        var prefs = AppPreferences.getInstance();
        var chatPrefs = prefs.getChatPreferences();
        showUserName.bind(chatPrefs.showUserNameProperty());
        showBadges.bind(chatPrefs.showBadgesProperty());
        font.bind(chatPrefs.fontProperty());
        chatMessageFilter.bind(prefs.getMessageFilterPreferences().chatMessageFilterProperty());
    }

    public ObservableList<ChatRoomViewModel> getChatRoomList() {
        return chatRoomList;
    }

    public FXTask<Void> loadAsync() {
        if (loaded.get()) return FXTask.empty();

        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        var task = FXTask.task(() -> {
            var globalBadges = new GlobalChatBadges();
            globalBadges.load(twitch);
            globalBadgeStore = new GlobalChatBadgeStore(globalBadges);
            chatEmoteStore = new ChatEmoteStore();
        });
        FXTask.setOnSucceeded(task, e -> loaded.set(true));

        TaskWorker.getInstance().submit(task);

        return task;
    }

    public ChatRoomViewModel register(TwitchChannel channel) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        var exist = chatRoomList.stream().filter(vm -> vm.hasChannel(channel)).findFirst();
        if (exist.isPresent()) {
            return exist.get();
        }

        exist = floatingChatRoomList.stream().filter(vm -> vm.hasChannel(channel)).findFirst();
        if (exist.isPresent()) {
            return exist.get();
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

        return chatRoomViewModel;
    }

    private void bindChatRoomProperties(ChatRoomViewModel viewModel) {
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
                task.get();
            } catch (InterruptedException | ExecutionException ignored) {
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
