package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.twitch4j.helix.domain.ChatBadgeSet;
import com.github.twitch4j.helix.domain.Stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * チャットを視聴するときに必要な情報を持つクラス。
 */
public class TwitchChannel {

    private final Broadcaster broadcaster;

    private Stream stream;

    private List<ChatBadgeSet> badgeSets = null;

    private final AtomicBoolean badgeLoaded = new AtomicBoolean(false);

    private final Twitch twitch;

    TwitchChannel(Twitch twitch, Broadcaster broadcaster, Stream stream) {
        this.twitch = twitch;
        this.broadcaster = broadcaster;
        this.stream = stream;
    }

    public void loadBadgesIfNotLoaded() {
        if (badgeLoaded.get()) return;

        var client = twitch.getClient();
        var token = twitch.getAccessToken();
        var helix = client.getHelix();

        // チャンネルのバッジを取得
        var badgeCommand = helix.getChannelChatBadges(token, broadcaster.getUserId());
        var badges = badgeCommand.execute().getBadgeSets();

        badgeSets = new ArrayList<>(badges);

        badgeLoaded.set(true);
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public String getBroadcasterId() {
        return broadcaster.getUserId();
    }

    public String getBroadcasterLogin() {
        return broadcaster.getUserLogin();
    }

    public String getBroadcasterName() {
        return broadcaster.getUserName();
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    public Stream getStream() {
        return stream;
    }

    public boolean isStreaming() {
        return stream != null;
    }

    public String getCurrentTitle() {
        return stream == null ? "" : stream.getTitle();
    }

    public String getCurrentGameName() {
        return stream == null ? "" : stream.getGameName();
    }

    public Optional<String> getBadgeUrl(ChatBadge badge) {
        if (!badgeLoaded.get()) throw new IllegalStateException("badges have not been loaded");

        for (var badgeSet : badgeSets) {
            if (!badgeSet.getSetId().equalsIgnoreCase(badge.id())) continue;

            for (var b : badgeSet.getVersions()) {
                if (!b.getId().equalsIgnoreCase(badge.version())) continue;

                return Optional.of(b.getSmallImageUrl());
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return "TwitchChannel{" +
                "broadcaster=" + broadcaster +
                ", stream=" + stream +
                '}';
    }
}
