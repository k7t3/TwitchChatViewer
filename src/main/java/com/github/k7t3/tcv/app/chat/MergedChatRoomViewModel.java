package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.emoji.EmojiImageCache;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import de.saxsys.mvvmfx.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class MergedChatRoomViewModel extends ChatRoomViewModel implements ViewModel {

    private static final int DEFAULT_ITEM_COUNT_LIMIT = 512;

    private final ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> channels = FXCollections.observableHashMap();

    MergedChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            List<SingleChatRoomViewModel> chatRooms,
            EmojiImageCache emojiCache,
            ChatRoomContainerViewModel containerViewModel
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors, emojiCache, containerViewModel);
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

        channels.remove(channel);

        // 分離するチャンネルのチャットを取り出す
        var chatList = getChatDataList().stream()
                .filter(c -> c.getChannel().equals(channel))
                .toList();

        // 分離したチャンネルにチャット情報を渡す
        chatRoom.getChatDataList().setAll(chatList);

        // 現在のチャットから削除
        getChatDataList().removeAll(chatList);

        containerViewModel.getChatRoomList().add(chatRoom);

        onChannelRemoved();
    }

    public void closeChatRoom(SingleChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channels.remove(channel);

        onChannelRemoved();
    }

    public void aggregate(MergedChatRoomViewModel mergedChatRoom) {
        if (this == mergedChatRoom) return;

        for (var entry : mergedChatRoom.channels.entrySet()) {
            var channel = entry.getKey();
            var chatRoom = entry.getValue();

            if (channels.containsValue(chatRoom)) continue;

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

            channels.put(channel, chatRoom);
            containerViewModel.getChatRoomList().remove(mergedChatRoom);
        }

    }

    public ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> getChannels() {
        return channels;
    }

    @Override
    public boolean accept(TwitchChannel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.getChannel().equals(channel))
                return true;
        }
        return false;
    }

    @Override
    public void onStateUpdated(ChatRoomStateUpdatedEvent e) {
        var chatRoom = e.getChatRoom();

        // ConcurrentModificationExceptionが内部で起こり得る？
        channels.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getChannel().isChatJoined())
                .filter(entry -> entry.getKey().getChannel().getOrJoinChatRoom().equals(chatRoom))
                .findFirst()
                .map(Map.Entry::getValue)
                .ifPresent(chatRoomViewModel -> chatRoomViewModel.onStateUpdated(e));
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

        channels.keySet().parallelStream().map(TwitchChannelViewModel::leaveChatAsync).forEach(task -> {
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

}
