package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VideoClipRepository {

    private final ConcurrentHashMap<VideoClip, PostedClip> clips = new ConcurrentHashMap<>();

    public VideoClipRepository() {
    }

    public void posted(Broadcaster channelOwner, VideoClip clip) {
        var posted = clips.computeIfAbsent(clip, PostedClip::new);
        posted.count++;
        posted.postedAt = LocalDateTime.now();
        posted.channelOwners.add(channelOwner);
    }

    public void remove(PostedClip clip) {
        clips.remove(clip.getClip());
    }

    public Set<Broadcaster> getChannelOwners() {
        return clips.values().stream()
                .flatMap(c -> c.channelOwners.stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Collection<PostedClip> getClips() {
        return clips.values();
    }

    public int getClipCount() {
        return clips.size();
    }

    public void clear() {
        clips.clear();
    }

}
