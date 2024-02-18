package com.github.k7t3.tcv.domain.clip;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.VideoClip;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class VideoClipRepository {

    private final LoadingCache<VideoClip, PostedClip> clips;
    private final Set<Broadcaster> channelOwners = new HashSet<>();

    public VideoClipRepository() {
        clips = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofHours(8))
                .build(PostedClip::new);
    }

    public void posted(Broadcaster channelOwner, VideoClip clip) {
        channelOwners.add(channelOwner);
        var posted = clips.get(clip);
        posted.posted(channelOwner);
    }

    public Set<Broadcaster> getChannelOwners() {
        return Set.copyOf(channelOwners);
    }

    public Collection<PostedClip> getClips() {
        return clips.asMap().values();
    }

    public int getClipCount() {
        return (int) clips.estimatedSize();
    }

}
