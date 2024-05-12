package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;

import java.util.Collections;
import java.util.List;

/**
 * チャットを開くイベント
 */
public class ChatOpeningEvent extends Event {

    private final MultipleChatOpenType chatOpenType;

    private final List<TwitchChannelViewModel> channels;

    public ChatOpeningEvent(TwitchChannelViewModel channel) {
        this(MultipleChatOpenType.SEPARATED, List.of(channel));
    }

    public ChatOpeningEvent(
            MultipleChatOpenType chatOpenType,
            List<TwitchChannelViewModel> channels
    ) {
        this.chatOpenType = chatOpenType;
        this.channels = Collections.unmodifiableList(channels);
    }

    /**
     * 複数のチャンネルが含まれるときに開くタイプを返す
     * @return 複数のチャンネルが含まれるときに開くタイプ
     */
    public MultipleChatOpenType getChatOpenType() {
        return chatOpenType;
    }

    /**
     * チャットが開かれたチャンネルのリストを返す
     * @return チャットが開かれたチャンネルのリスト
     */
    public List<TwitchChannelViewModel> getChannels() {
        return channels;
    }

}
