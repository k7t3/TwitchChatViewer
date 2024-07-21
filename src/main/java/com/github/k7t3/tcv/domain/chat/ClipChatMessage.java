/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public static ClipChatMessage of(String id, String url, String plainMessage, VideoClip clip) {
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

    @Override
    public String toString() {
        return "ClipChatMessage{" +
                "clip=" + clip +
                ", id='" + id + '\'' +
                ", estimatedURL='" + estimatedURL + '\'' +
                ", plainMessage='" + plainMessage + '\'' +
                '}';
    }
}
