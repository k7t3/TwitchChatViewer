package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.clip.VideoClip;

import java.util.Optional;

/**
 * チャットに含まれるクリップ情報
 */
public class ClipChatMessage {

    private final VideoClip clip;

    private final String estimatedURL;

    private final String plainMessage;

    private ClipChatMessage(VideoClip clip, String estimatedURL, String plainMessage) {
        this.clip = clip;
        this.estimatedURL = estimatedURL;
        this.plainMessage = plainMessage;
    }

    public static ClipChatMessage of(String url, String plainMessage, VideoClip clip) {
        return new ClipChatMessage(clip, url, plainMessage);
    }

    public static ClipChatMessage of(String url, String plainMessage) {
        return new ClipChatMessage(null, url, plainMessage);
    }

    public Optional<VideoClip> getClip() {
        return Optional.ofNullable(clip);
    }

    public String getEstimatedURL() {
        return estimatedURL;
    }

    public String getPlainMessage() {
        return plainMessage;
    }
}
