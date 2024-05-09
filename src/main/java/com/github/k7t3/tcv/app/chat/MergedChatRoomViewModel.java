package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MergedChatRoomViewModel extends ChatRoomViewModel implements ViewModel {

    private static final int DEFAULT_ITEM_COUNT_LIMIT = 512;

    private final ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> channels = FXCollections.observableHashMap();

    MergedChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            List<SingleChatRoomViewModel> chatRooms,
            ChatRoomContainerViewModel containerViewModel
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors, containerViewModel);
        setChatCacheSize(DEFAULT_ITEM_COUNT_LIMIT);

        initChatRooms(chatRooms);
    }

    private void initChatRooms(List<SingleChatRoomViewModel> chatRooms) {
        for (var chatRoom : chatRooms) {
            addChatRoom(chatRoom);
        }
    }

    @Override
    public String getIdentity() {
        var joined = channels.keySet().stream()
                .map(TwitchChannelViewModel::getUserLogin)
                .sorted()
                .collect(Collectors.joining());
        return Base64.getEncoder().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    public void addChatRoom(SingleChatRoomViewModel chatRoom) {
        var channel = chatRoom.getChannel();

        channel.getChatRoomListeners().remove(chatRoom);
        channel.getChannelListeners().remove(chatRoom);

        //
        // 追加するチャットルームが現在持っているチャットの情報をマージする
        // とりあえずすべてマージして最後に制限の個数で切り捨てる
        //
        var mergedChats = new ArrayList<>(getChatDataList());
        mergedChats.addAll(chatRoom.getChatDataList());
        mergedChats.sort(Comparator.comparing(o -> o.getChatData().firedAt()));

        var limit = getChatCacheSize();
        if (mergedChats.size() <= limit) {
            getChatDataList().setAll(mergedChats);
        } else {
            var subList = mergedChats.subList(mergedChats.size() - limit, mergedChats.size());
            getChatDataList().setAll(subList);
        }

        channel.getChatRoomListeners().add(this);
        channel.getChannelListeners().add(this);

        channels.put(channel, chatRoom);
    }

    /**
     * チャンネルが削除されたとき、残りをチャンネル数を確認して
     * 最後の一つであれば個別のチャンネルとして切り離し、このインスタンスは取り除く。
     */
    private void onChannelRemoved() {
        if (channels.size() != 1) {
            return;
        }

        var chatRoom = channels.entrySet().stream().findFirst().orElseThrow().getValue();
        separateChatRoom(chatRoom);

        containerViewModel.getChatRoomList().remove(this);
    }

    public void separateAll() {
        var channels = new ArrayList<>(this.channels.values());
        channels.forEach(this::separateChatRoom);
    }

    public void separateChatRoom(SingleChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channel.getChatRoomListeners().remove(this);
        channel.getChannelListeners().remove(this);

        channels.remove(channel);

        // 分離するチャンネルのチャットを取り出す
        var chatList = getChatDataList().stream()
                .filter(c -> c.getChannel().equals(channel))
                .toList();

        // 分離したチャンネルにチャット情報を渡す
        chatRoom.getChatDataList().setAll(chatList);

        // 現在のチャットから削除
        getChatDataList().removeAll(chatList);

        channel.getChannelListeners().add(chatRoom);
        channel.getChatRoomListeners().add(chatRoom);

        containerViewModel.getChatRoomList().add(chatRoom);

        onChannelRemoved();
    }

    public void closeChatRoom(SingleChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channel.getChatRoomListeners().remove(this);
        channel.getChannelListeners().remove(this);

        channels.remove(channel);

        onChannelRemoved();
    }

    public void aggregate(MergedChatRoomViewModel mergedChatRoom) {
        if (this == mergedChatRoom) return;

        for (var entry : mergedChatRoom.channels.entrySet()) {
            var channel = entry.getKey();
            var chatRoom = entry.getValue();

            if (!channels.containsValue(chatRoom)) continue;

            channel.getChatRoomListeners().remove(mergedChatRoom);
            channel.getChannelListeners().remove(mergedChatRoom);

            //
            // 追加するチャットルームが現在持っているチャットの情報をマージする
            // とりあえずすべてマージして、最後に制限の個数で切り捨てる
            //
            var mergedChats = new ArrayList<>(getChatDataList());
            mergedChats.addAll(chatRoom.getChatDataList());
            mergedChats.sort(Comparator.comparing(o -> o.getChatData().firedAt()));

            var limit = getChatCacheSize();
            if (mergedChats.size() <= limit) {
                chatRoom.getChatDataList().setAll(mergedChats);
            } else {
                var subList = mergedChats.subList(mergedChats.size() - limit, mergedChats.size());
                chatRoom.getChatDataList().setAll(subList);
            }

            channel.getChatRoomListeners().add(this);
            channel.getChannelListeners().add(this);

            channels.put(channel, chatRoom);
            containerViewModel.getChatRoomList().remove(mergedChatRoom);
        }

    }

    public ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> getChannels() {
        return channels;
    }

    @Override
    boolean hasChannel(TwitchChannelViewModel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.equals(channel))
                return true;
        }
        return false;
    }

    @Override
    protected TwitchChannelViewModel getChannel(TwitchChannel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.getChannel().equals(channel))
                return channelViewModel;
        }
        throw new IllegalArgumentException("Unexpected channel: " + channel.toString());
    }

    @Override
    public FXTask<?> joinChatAsync() {
        // このインスタンスはチャットへ参加済みのチャンネルしか扱わないため
        throw new UnsupportedOperationException();
    }

    @Override
    public FXTask<?> leaveChatAsync() {

        containerViewModel.onLeft(this);

        // FIXME JavaFXのTaskをマージするには？
        var latch = new CountDownLatch(channels.size());

        channels.keySet().stream().map(TwitchChannelViewModel::leaveChatAsync).forEach(task -> {
            try {
                task.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                // no-op
            } finally {
                latch.countDown();
            }
        });

        return FXTask.task(() -> {
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                // no-op
            }
        });
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, ClipChatMessage clipChatMessage) {
        // no-op
    }

    @Override
    public void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active) {
        // ConcurrentModificationExceptionが内部で起こり得る？
        channels.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getChannel().isChatJoined() && entry.getKey().getChannel().getOrJoinChatRoom().equals(chatRoom))
                .findFirst()
                .map(Map.Entry::getValue)
                .ifPresent(chatRoomViewModel -> chatRoomViewModel.onStateUpdated(chatRoom, roomState, active));
    }

    private void updateStreamInfo(TwitchChannel channel, StreamInfo streamInfo) {
        Platform.runLater(() -> {
            for (var channelViewModel : channels.keySet()) {
                if (channelViewModel.getChannel().equals(channel))
                    channelViewModel.updateStreamInfo(streamInfo);
            }
        });
    }

    @Override
    public void onOnline(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onOffline(TwitchChannel channel) {
        updateStreamInfo(channel, null);
    }

    @Override
    public void onViewerCountUpdated(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onTitleChanged(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onGameChanged(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }
}
