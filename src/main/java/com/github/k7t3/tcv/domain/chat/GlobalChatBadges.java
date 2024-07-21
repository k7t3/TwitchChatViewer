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
import com.github.twitch4j.helix.domain.ChatBadgeSet;

import java.util.List;
import java.util.Optional;

public class GlobalChatBadges {

    private List<ChatBadgeSet> badgeSets;

    private boolean loaded = false;

    public GlobalChatBadges() {
    }

    public void load(Twitch twitch) {
        if (loaded) return;

        var api = twitch.getTwitchAPI();
        badgeSets = api.getGlobalBadgeSet();

        loaded = true;
    }

    public Optional<String> getBadgeUrl(String id, String version) {
        if (!loaded) throw new RuntimeException("not loaded yet");

        for (var badgeSet : badgeSets) {
            if (!badgeSet.getSetId().equalsIgnoreCase(id)) continue;

            for (var badge : badgeSet.getVersions()) {
                if (!badge.getId().equalsIgnoreCase(version)) continue;

                return Optional.of(badge.getSmallImageUrl());
            }
        }

        return Optional.empty();
    }
}
