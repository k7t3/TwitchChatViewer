package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.emoji.ChatEmojiStore;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** 初期ロードの結果、画像キャッシュと絵文字アーカイブを所有する。未採用の結果も所有者が閉じる。 */
public final class ChatResources implements AutoCloseable {
    private final Emoji emoji;
    private final GlobalChatBadges badges;
    private final ChatFilters.Snapshot filters;
    private final GlobalChatBadgeStore badgeStore;
    private final ChatEmoteStore emoteStore;
    private final ChatEmojiStore emojiStore;
    private final AtomicBoolean closed = new AtomicBoolean();

    ChatResources(Emoji emoji, GlobalChatBadges badges, ChatFilters.Snapshot filters, ImageLoader images) {
        this.emoji = Objects.requireNonNull(emoji);
        this.badges = Objects.requireNonNull(badges);
        this.filters = Objects.requireNonNull(filters);
        badgeStore = new GlobalChatBadgeStore(badges, images);
        emoteStore = new ChatEmoteStore(images);
        emojiStore = new ChatEmojiStore(emoji, images);
    }

    Emoji emoji() { return emoji; }
    GlobalChatBadges badges() { return badges; }
    ChatFilters.Snapshot filters() { return filters; }
    GlobalChatBadgeStore badgeStore() { return badgeStore; }
    ChatEmoteStore emoteStore() { return emoteStore; }
    ChatEmojiStore emojiStore() { return emojiStore; }

    /** 画像の取消を要求してからアーカイブを一度だけ閉じる。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            CloseActions.runAll(badgeStore::close, emoteStore::close, emojiStore::close, emoji::close);
        }
    }
}
