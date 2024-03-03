package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.main.MainViewModel;
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
import javafx.scene.text.Font;

import java.util.List;

public class ChatRoomContainerViewModel implements ViewModel {

    /** 開かれているチャット(チャンネル)のリスト*/
    private final ObservableList<ChatRoomViewModelBase> chatList = FXCollections.observableArrayList(c -> new Observable[] { c.selectedProperty() });

    /** 選択しているチャット*/
    private final ObservableList<ChatRoomViewModelBase> selectedList = new FilteredList<>(chatList, ChatRoomViewModelBase::isSelected);

    /** 選択モード*/
    private final ReadOnlyBooleanWrapper selectMode = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyIntegerWrapper selectingCount = new ReadOnlyIntegerWrapper();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private GlobalChatBadgeStore globalBadgeStore;

    private ChatEmoteStore chatEmoteStore;

    private final DefinedChatColors definedChatColors = new DefinedChatColors();

    private final BooleanProperty showUserName = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final ObjectProperty<Font> font = new SimpleObjectProperty<>(null);

    private final ObjectProperty<ChatMessageFilter> chatMessageFilter = new SimpleObjectProperty<>(ChatMessageFilter.DEFAULT);

    private MainViewModel mainViewModel;

    private List<ChatRoomListener> defaultChatRoomListeners;

    public ChatRoomContainerViewModel() {
        initialize();
    }

    private void initialize() {
        var helper = AppHelper.getInstance();

        helper.authorizedProperty().addListener((ob, o, n) -> {
            if (!n) {
                chatList.clear();
            }
        });

        selectingCount.bind(Bindings.size(selectedList));

        // 一つ以上選択されているときは選択モード
        selectMode.bind(selectingCount.greaterThan(0));
    }

    public ObservableList<ChatRoomViewModelBase> getChatList() {
        return chatList;
    }

    public void installMainViewModel(MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
        defaultChatRoomListeners = List.of(mainViewModel.createClipPostListener());

        // Preferencesと同期
        var prefs = AppPreferences.getInstance();
        var chatPrefs = prefs.getChatPreferences();
        showUserName.bind(chatPrefs.showUserNameProperty());
        showBadges.bind(chatPrefs.showBadgesProperty());
        font.bind(chatPrefs.fontProperty().map(ChatFont::getFont));
        chatMessageFilter.bind(prefs.getMessageFilterPreferences().messageFilterProperty());
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

    public ChatRoomViewModelBase register(TwitchChannel channel) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        var exist = chatList.stream().filter(vm -> vm.hasChannel(channel)).findFirst();

        if (exist.isPresent()) {
            return exist.get();
        }

        var chatRoomViewModel = new ChatRoomViewModel(
                this,
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                channel
        );

        var channelViewModel = chatRoomViewModel.getChannel();
        channelViewModel.getChatRoomListeners().addAll(defaultChatRoomListeners);

        bindChatRoomProperties(chatRoomViewModel);

        chatList.add(chatRoomViewModel);

        return chatRoomViewModel;
    }

    private void bindChatRoomProperties(ChatRoomViewModelBase viewModel) {
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
    void onLeft(ChatRoomViewModelBase chat) {
        chatList.removeIf(vm -> vm.equals(chat));
    }

    public ObservableList<ChatRoomViewModelBase> getSelectedList() {
        return selectedList;
    }

    public void unselectAll() {
        chatList.forEach(c -> c.setSelected(false));
    }

    public void mergeSelectedChats() {

        var chatRooms = getSelectedList().stream()
                .filter(vm -> vm instanceof ChatRoomViewModel)
                .map(vm -> (ChatRoomViewModel) vm)
                .toList();

        var chatRoomViewModel = new MergedChatRoomViewModel(
                globalBadgeStore,
                chatEmoteStore,
                definedChatColors,
                chatRooms,
                this
        );

        bindChatRoomProperties(chatRoomViewModel);

        chatList.removeAll(getSelectedList());
        chatList.add(chatRoomViewModel);
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
