package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;

import java.time.Duration;

public class GlobalChatBadgeStore extends CachedImageStore<ChatBadge> {

    private final GlobalChatBadges globalBadges;

    public GlobalChatBadgeStore(GlobalChatBadges globalChatBadges) {
        super(16, Duration.ofMinutes(5));
        this.globalBadges = globalChatBadges;
    }

    @Override
    protected LazyImage loadImage(ChatBadge key) {
        var url = globalBadges.getBadgeUrl(key.id(), key.version()).orElse(null);
        if (url == null) return null;
        return new LazyImage(url, 0, 0);
    }

}
