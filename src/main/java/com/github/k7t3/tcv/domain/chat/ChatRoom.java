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

package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatRoom {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoom.class);

    private final Broadcaster broadcaster;

    private final TwitchChannel channel;

    private final Twitch twitch;

    public ChatRoom(Twitch twitch, Broadcaster broadcaster, TwitchChannel channel) {
        this.twitch = twitch;
        this.channel = channel;
        this.broadcaster = broadcaster;

        var chat = twitch.getChat();
        chat.joinChannel(getBroadcaster().getUserLogin());
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    public void leave() {
        LOGGER.info("{} leave chat", getBroadcaster().getUserLogin());

        var chat = twitch.getChat();
        chat.leaveChannel(getBroadcaster().getUserLogin());
    }

}
