package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ClipFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipFinder.class);

    private static final Pattern CLIP_URL_PATTERN = Pattern.compile(
            "https://((www\\.twitch\\.tv|m\\.twitch\\.tv)/[^/]+/clip|clips\\.twitch\\.tv)/[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']+"
    );

    private final Twitch twitch;

    public ClipFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    URI parseClipURI(String chatMessage) {
        var matcher = CLIP_URL_PATTERN.matcher(chatMessage);
        if (!matcher.find()) {
            return null;
        }

        try {
            return new URI(matcher.group().trim());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * チャットに投稿されたクリップのURLをパースしてAPIに投げるメソッド
     */
    public Optional<ClipChatMessage> findClip(String message) {
        try {
            var uri = parseClipURI(message);
            if (uri == null) {
                return Optional.empty();
            }

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
                LOGGER.warn("clip not found clip_id={} ({})", clipId, uri);
                return Optional.of(ClipChatMessage.of(clipId, uri.toString(), message));
            }

            return Optional.of(ClipChatMessage.of(clipId, uri.toString(), message, VideoClip.of(clips.getFirst())));

        } catch (Exception e) {
            LOGGER.error(message, e);
            return Optional.empty();
        }
    }

}
