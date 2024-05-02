package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.clip.VideoClip;

import java.util.Optional;

/**
 * チャットに含まれるクリップ情報
 * <p>
 *     Twitch APIの仕様なのか、取得できないIDのクリップは
 *     尽く取得できないので{@link ClipChatMessage#getClip()}はNullableを返す。
 * </p>
 */
public class ClipChatMessage {

    private final VideoClip clip;

    private final String id;

    private final String estimatedURL;

    private final String plainMessage;

    private ClipChatMessage(VideoClip clip, String id, String estimatedURL, String plainMessage) {
        this.clip = clip;
        this.id = id;
        this.estimatedURL = estimatedURL;
        this.plainMessage = plainMessage;
    }

    public static ClipChatMessage of(String url, String id, String plainMessage, VideoClip clip) {
        return new ClipChatMessage(clip, id, url, plainMessage);
    }

    public static ClipChatMessage of(String id, String url, String plainMessage) {
        return new ClipChatMessage(null, id, url, plainMessage);
    }

    public Optional<VideoClip> getClip() {
        return Optional.ofNullable(clip);
    }

    public String getId() {
        return id;
    }

    public String getEstimatedURL() {
        return estimatedURL;
    }

    public String getPlainMessage() {
        return plainMessage;
    }
}
