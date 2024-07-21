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

package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;

import java.util.Collections;
import java.util.List;

/**
 * チャットを開くイベント
 */
public class ChatOpeningEvent implements AppEvent {

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
