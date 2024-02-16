package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import javafx.scene.image.Image;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class ChannelChatBadgeStore extends CachedImageStore<ChatBadge> {

    private final TwitchChannel channel;

    public ChannelChatBadgeStore(TwitchChannel channel) {
        super(8, Duration.ofMinutes(5));
        this.channel = channel;
    }

    @Override
    protected @Nullable Image loadImage(ChatBadge key) {
        var url = channel.getBadgeUrl(key).orElse(null);
        if (url == null) return null;
        return new Image(url, 0, 0, true, true, true);
    }
}
