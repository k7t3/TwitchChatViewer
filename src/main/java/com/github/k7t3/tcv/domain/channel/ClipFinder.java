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

    private static final Pattern CLIP_URL_PATTERN = Pattern.compile("^https://(www.twitch.tv|m.twitch.tv)/[^/]+/clip/[^ ]+");

    private final Twitch twitch;

    public ClipFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    /**
     * チャットに投稿されたクリップのURLをパースしてAPIに投げるメソッド
     */
    public Optional<VideoClip> findClip(String link) {
        if (!CLIP_URL_PATTERN.matcher(link).hasMatch()) {
            return Optional.empty();
        }

        try {
            var uri = new URI(link);

            // 0: empty
            // 1: ユーザー名
            // 2: clip
            // 3: クリップのID
            var paths = uri.getPath().split("/");
            if (paths.length != 4) {
                LOGGER.error("unexpected clip url {}", link);
                return Optional.empty();
            }

            var userLogin = paths[1];
            var clipId = paths[3];

            var client = twitch.getClient();
            if (client == null) {
                LOGGER.warn("client is null");
                return Optional.empty();
            }

            var helix = client.getHelix();
            var clipsCommand = helix.getClips(
                    twitch.getAccessToken(),
                    userLogin,
                    null,
                    List.of(clipId),
                    null,
                    null,
                    1,
                    null,
                    null,
                    null
            );

            var clips = clipsCommand.execute().getData();
            if (clips.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(VideoClip.of(clips.getFirst()));

        } catch (Exception e) {
            LOGGER.error("unexpected clip url {}", link);
            return Optional.empty();
        }
    }

}
