package com.github.k7t3.tcv.vm.chat;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoomRepository;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.service.FXTask;
import com.github.k7t3.tcv.vm.service.TaskWorker;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.Closeable;

public class ChatContainerViewModel implements ViewModel, Closeable {

    /** 開かれているチャット(チャンネル)のリスト*/
    private final ObservableList<ChatViewModel> chatList = FXCollections.observableArrayList();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private ChatRoomRepository repository;
    private GlobalChatBadgeStore globalBadgeStore;

    private final DefinedChatColors definedChatColors = new DefinedChatColors();

    public ChatContainerViewModel() {
    }

    public ObservableList<ChatViewModel> getChatList() {
        return chatList;
    }

    public FXTask<Void> loadAsync() {
        if (loaded.get()) throw new IllegalStateException("already loaded");

        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        var task = FXTask.task(() -> {
            repository = new ChatRoomRepository(twitch);
            repository.load();
            var globalBadges = new GlobalChatBadges(twitch);
            globalBadges.load();
            globalBadgeStore = new GlobalChatBadgeStore(globalBadges);
        });
        FXTask.setOnSucceeded(task, e -> loaded.set(true));

        TaskWorker.getInstance().submit(task);

        return task;
    }

    /**
     * チャンネルのChatインスタンスを取得する。
     * チャットに接続されているかは{@link ChatViewModel#chatJoinedProperty()}で参照できる。
     * @param channel チャンネル
     * @return Chat
     */
    public ChatViewModel register(TwitchChannel channel) {
        if (!loaded.get()) throw new IllegalStateException("not loaded yet");

        var exist = chatList.stream().filter(vm ->
                        vm.getChannel().getBroadcaster().equals(channel.getBroadcaster())
                )
                .findFirst();

        if (exist.isPresent()) {
            return exist.get();
        }

        var viewModel = new ChatViewModel(channel, repository, globalBadgeStore, definedChatColors, this);
        chatList.add(viewModel);
        return viewModel;
    }

    /**
     * 管理しているChatリストから削除する。
     * チャットから切断することはしない。切断済みのものを渡すこと。
     * @param chat チャット
     */
    void left(ChatViewModel chat) {
        chatList.removeIf(vm -> vm.equals(chat));
    }

    @Override
    public void close() {
        if (repository == null) return;
        repository.close();
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyBooleanWrapper loadedWrapper() { return loaded; }
    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }
    private void setLoaded(boolean loaded) { this.loaded.set(loaded); }
}
