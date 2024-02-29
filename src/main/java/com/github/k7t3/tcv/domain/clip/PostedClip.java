package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * チャットに投稿されたクリップ
 */
public class PostedClip {

    int count = 0;

    LocalDateTime postedAt;

    final Set<Broadcaster> channelOwners = new HashSet<>();

    private final VideoClip clip;

    public PostedClip(VideoClip clip) {
        this.clip = clip;
    }

    public VideoClip getClip() {
        return clip;
    }

    public int getPostCount() {
        return count;
    }

    public LocalDateTime getLatestTime() {
        return postedAt;
    }

    public boolean isPosted(Broadcaster channelOwner) {
        return channelOwners.contains(channelOwner);
    }

}
