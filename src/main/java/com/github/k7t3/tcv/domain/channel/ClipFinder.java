package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ClipFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipFinder.class);

    private static final Pattern CLIP_URL_PATTERN = Pattern.compile("https://((www\\.twitch\\.tv|m\\.twitch\\.tv)/[^/]+/clip|clips\\.twitch\\.tv)/[^ 　]+");

    private final Twitch twitch;

    public ClipFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    /**
     * チャットに投稿されたクリップのURLをパースしてAPIに投げるメソッド
     */
    public Optional<VideoClip> findClip(String message) {
        var matcher = CLIP_URL_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }

        var link = matcher.group().trim();

        try {
            var uri = new URI(link);

            // 0: empty
            // 1: ユーザー名
            // 2: clip
            // 3: クリップのID
            var paths = uri.getPath().split("/");
            if (paths.length != 4 && paths.length != 2) {
                LOGGER.error("unexpected clip url {}", message);
                return Optional.empty();
            }

            var clipId = paths[paths.length - 1];

            var api = twitch.getTwitchAPI();
            var clips = api.getClips(List.of(clipId));

            if (clips.isEmpty()) {
                LOGGER.warn("clip not found clip_id={} ({})", clipId, link);
                return Optional.empty();
            }

            return Optional.of(VideoClip.of(clips.getFirst()));

        } catch (Exception e) {
            LOGGER.error(message, e);
            return Optional.empty();
        }
    }

}
