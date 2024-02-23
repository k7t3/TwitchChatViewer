package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.VideoClip;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VideoClipRepository {

    private final ConcurrentHashMap<VideoClip, PostedClip> clips = new ConcurrentHashMap<>();
    private final Set<Broadcaster> channelOwners = new HashSet<>();

    public VideoClipRepository() {
    }

    public void posted(Broadcaster channelOwner, VideoClip clip) {
        channelOwners.add(channelOwner);
        var posted = clips.computeIfAbsent(clip, PostedClip::new);
        posted.posted(channelOwner);
    }

    public void remove(PostedClip clip) {

        clips.remove(clip.getClip());

        var broadcasters = clip.getBroadcasters();

        // 削除したクリップにだけ紐づいていたBroadcasterを削除する
        for (var broadcaster : broadcasters) {

            var none = clips.values().stream().noneMatch(c -> c.isPosted(broadcaster));
            if (none) {
                channelOwners.remove(broadcaster);
            }
        }
    }

    public Set<Broadcaster> getChannelOwners() {
        //return clips.values().stream().map(PostedClip::getBroadcasters).flatMap(Collection::stream).collect(Collectors.toSet());
        return Set.copyOf(channelOwners);
    }

    public Collection<PostedClip> getClips() {
        return clips.values();
    }

    public int getClipCount() {
        return clips.size();
    }

}
