package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.VideoClip;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * チャットに投稿されたクリップ
 */
public class PostedClip {

    /** 投稿されたチャンネルと時刻*/
    private record PostInfo(Broadcaster channelOwner, LocalDateTime postedAt) {}

    private final VideoClip clip;

    private final List<PostInfo> posted = new ArrayList<>();

    public PostedClip(VideoClip clip) {
        this.clip = clip;
    }

    public VideoClip getClip() {
        return clip;
    }

    public int getPostCount() {
        return posted.size();
    }

    public LocalDateTime getLatestTime() {
        return posted.getLast().postedAt();
    }

    void posted(Broadcaster channelOwner) {
        posted.add(new PostInfo(channelOwner, LocalDateTime.now()));
    }

    Set<Broadcaster> getBroadcasters() {
        return posted.stream().map(p -> p.channelOwner).collect(Collectors.toSet());
    }

    public boolean isPosted(Broadcaster channelOwner) {
        for (var info : posted) {
            if (info.channelOwner.equals(channelOwner)) {
                return true;
            }
        }
        return false;
    }

}
