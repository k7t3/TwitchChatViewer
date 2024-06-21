package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class ChannelChatBadgeStore extends CachedImageStore<ChatBadge> {

    private final TwitchChannel channel;

    public ChannelChatBadgeStore(TwitchChannel channel) {
        super(8, Duration.ofMinutes(5));
        this.channel = channel;
    }

    @Override
    protected @Nullable LazyImage loadImage(ChatBadge key) {
        var url = channel.getBadgeUrl(key).orElse(null);
        if (url == null) return null;
        return new LazyImage(url, 24, 24);
    }
}
