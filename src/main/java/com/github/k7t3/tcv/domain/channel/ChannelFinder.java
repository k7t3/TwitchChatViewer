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

package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;

import java.util.List;

public class ChannelFinder {

    private final Twitch twitch;

    public ChannelFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    public List<FoundChannel> search(String startsWith, boolean liveOnly) {

        var api = twitch.getTwitchAPI();
        return api.search(startsWith, liveOnly);

    }
}
