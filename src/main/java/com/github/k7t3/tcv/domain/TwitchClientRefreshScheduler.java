package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.twitch4j.TwitchClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 有効期限付きのアクセストークンを定期的に更新するスケジューラ
 *
 * <p>
 *     このアプリケーションは認証方法としてDevice Code Flowを採用しているため
 *     定期的にトークンのリフレッシュが必要になるが、Twitch4Jがリフレッシュを
 *     サポートしていないため(Roadmapには登録されている)、使用するトークンを定期的に
 *     更新する必要がある。
 * </p>
 * <p>
 *     {@link com.github.twitch4j.TwitchClient}はトークンを差し替えることができないようなので
 *     リフレッシュ時にインスタンスそのものを入れ替えることで対応する。
 *     この弊害として{@code TwitchClient}を使ってイベントをリッスンしている場合は
 *     すべて付け替えてあげる必要があったり、クラスのプライベートフィールドで
 *     使用している場合はトークンの更新が反映できないため、使用の際はスコープを
 *     細かくしたメソッド引数などで受け取るべきである。
 * </p>
 */
class TwitchClientRefreshScheduler implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchClientRefreshScheduler.class);

    // 3h 30m
    private static final long DELAY_MINUTES = 210;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    private final Twitch twitch;

    public TwitchClientRefreshScheduler(Twitch twitch) {
        this.twitch = twitch;
    }

    public void start() {
        LOGGER.info("refresh scheduler started");

        executor.scheduleAtFixedRate(
                this::updateToken,
                DELAY_MINUTES,
                DELAY_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private void updateToken() {
        LOGGER.info("token update");

        var controller = new CredentialController();
        var credential = controller.refreshToken();

        var credentialManager = controller.getCredentialManager();
        var client = TwitchClientBuilder.builder()
                .withCredentialManager(credentialManager)
                .withDefaultAuthToken(credential)
                .withEnableHelix(true)
                .build();

        var olderClient = twitch.getClient();

        twitch.setCredential(credential);
        twitch.setClient(client);

        var repository = twitch.getChannelRepository();
        repository.updateAllEventListeners();

        olderClient.close();
    }

    @Override
    public void close() throws IOException {
        executor.close();
    }
}
