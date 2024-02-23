package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatFont;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;

import java.util.List;

public class ChatContainerViewModel implements ViewModel {

    /** 開かれているチャット(チャンネル)のリスト*/
    private final ObservableList<ChatViewModel> chatList = FXCollections.observableArrayList();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private GlobalChatBadgeStore globalBadgeStore;

    private final DefinedChatColors definedChatColors = new DefinedChatColors();

    private final BooleanProperty showUserName = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final ObjectProperty<Font> font = new SimpleObjectProperty<>(null);

    private MainViewModel mainViewModel;

    private List<ChatRoomListener> defaultChatRoomListeners;

    public ChatContainerViewModel() {
    }

    public ObservableList<ChatViewModel> getChatList() {
        return chatList;
    }

    public void installMainViewModel(MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
        defaultChatRoomListeners = List.of(mainViewModel.createClipPostListener());

        // Preferencesと同期
        var prefs = AppPreferences.getInstance();
        showUserName.bind(prefs.showUserNameProperty());
        showBadges.bind(prefs.showBadgesProperty());
        font.bind(prefs.fontProperty().map(ChatFont::getFont));
    }

    public FXTask<Void> loadAsync() {
        if (loaded.get()) throw new IllegalStateException("already loaded");

        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        var task = FXTask.task(() -> {
            var globalBadges = new GlobalChatBadges();
            globalBadges.load(twitch);
            globalBadgeStore = new GlobalChatBadgeStore(globalBadges);
        });
        FXTask.setOnSucceeded(task, e -> loaded.set(true));

        TaskWorker.getInstance().submit(task);

        return task;
    }

    public ChatViewModel register(TwitchChannel channel) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        var exist = chatList.stream().filter(vm ->
                        vm.getChannel().getBroadcaster().equals(channel.getBroadcaster())
                )
                .findFirst();

        if (exist.isPresent()) {
            return exist.get();
        }

        var viewModel = new ChatViewModel(
                channel,
                globalBadgeStore,
                definedChatColors,
                this,
                defaultChatRoomListeners
        );

        viewModel.visibleNameProperty().bind(showUserName);
        viewModel.visibleBadgesProperty().bind(showBadges);
        viewModel.fontProperty().bind(font);

        chatList.add(viewModel);

        return viewModel;
    }

    /**
     * 管理しているChatリストから削除する。
     * チャットから切断することはしない。切断済みのものを渡すこと。
     * @param chat チャット
     */
    void onLeft(ChatViewModel chat) {
        chatList.removeIf(vm -> vm.equals(chat));
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyBooleanWrapper loadedWrapper() { return loaded; }
    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }
    private void setLoaded(boolean loaded) { this.loaded.set(loaded); }
}
