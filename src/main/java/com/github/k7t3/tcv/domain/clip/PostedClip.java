package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.VideoClip;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PostedClip {

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

    public void posted(Broadcaster channelOwner) {
        posted.add(new PostInfo(channelOwner, LocalDateTime.now()));
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
