package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.service.CachedImageStore;
import javafx.scene.image.Image;

import java.time.Duration;

/**
 * チャットエモート画像ストア
 */
public class ChatEmoteStore extends CachedImageStore<String> {

    private static final String CDN_FORMAT = "https://static-cdn.jtvnw.net/emoticons/v2/%s/default/light/1.0";

    public ChatEmoteStore() {
        super(100, Duration.ofMinutes(5));
    }

    @Override
    protected Image loadImage(String key) {
        var uri = CDN_FORMAT.formatted(key);
        return new Image(uri, 0, 0, true, true, true);
    }

}
