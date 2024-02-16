package com.github.k7t3.tcv.domain.channel;

/**
 * 検索して見つかったチャンネル
 */
public class FoundChannel {

    private final Broadcaster broadcaster;

    private final boolean live;

    private final String gameName;

    public FoundChannel(Broadcaster broadcaster, boolean live, String gameName) {
        this.broadcaster = broadcaster;
        this.live = live;
        this.gameName = gameName;
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public boolean isLive() {
        return live;
    }

    public String getGameName() {
        return gameName;
    }

}
