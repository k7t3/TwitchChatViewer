package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** UI 状態を変更せず、チャットに必要なデータを読み込む。途中失敗時は開いたリソースを解放する。 */
public final class ChatResourcesLoader {
    /** 後始末の失敗を通常の取得失敗と区別する。原因とそのsuppressedに両方の失敗を保持する。 */
    static final class CleanupFailure extends IllegalStateException {
        private CleanupFailure(Throwable loadFailure) {
            super("Failed to release chat resources after loading failed", loadFailure);
        }
    }

    private final Supplier<Emoji> emojiFactory;
    private final Supplier<GlobalChatBadges> badgeLoader;
    private final Supplier<ChatFilters.Snapshot> filterLoader;
    private final ImageLoader images;

    /** 取得に使うセッションと画像サービスを借用し、背景で読む入力を固定する。 */
    public ChatResourcesLoader(Path applicationDirectory, Twitch twitch, Supplier<ChatFilters.Snapshot> filterLoader, ImageLoader images) {
        this(() -> new Emoji(applicationDirectory), () -> {
            var badges = new GlobalChatBadges();
            badges.load(twitch);
            return badges;
        }, filterLoader, images);
    }

    ChatResourcesLoader(Supplier<Emoji> emojiFactory, Supplier<GlobalChatBadges> badgeLoader,
                        Supplier<ChatFilters.Snapshot> filterLoader, ImageLoader images) {
        this.emojiFactory = Objects.requireNonNull(emojiFactory);
        this.badgeLoader = Objects.requireNonNull(badgeLoader);
        this.filterLoader = Objects.requireNonNull(filterLoader);
        this.images = Objects.requireNonNull(images);
    }

    /**
     * 背景で取得し、返した資源の所有を呼出元へ移す。途中失敗では自身で解放する。
     * @throws IOException アーカイブの配置・検証が失敗した場合
     * @throws IllegalStateException 途中失敗の解放も失敗した場合。元の取得失敗を原因とし、
     *         そのsuppressedに解放失敗を保持する
     */
    public ChatResources load() throws IOException {
        var emoji = Objects.requireNonNull(emojiFactory.get());
        try {
            if (!emoji.validateArchive()) {
                emoji.extractArchive();
                if (!emoji.validateArchive()) throw new IOException("emoji archive validation failed");
            }
            return new ChatResources(emoji, badgeLoader.get(), filterLoader.get(), images);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                emoji.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                throw new CleanupFailure(failure);
            }
            throw failure;
        }
    }
}
