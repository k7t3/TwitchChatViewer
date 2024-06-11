package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

public class TwitchLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchLoader.class);

    public record DeviceFlow(String userCode, String verificationURL) {}
    private final CredentialStore credentialStore;
    private final CredentialController controller;

    public TwitchLoader(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
        controller = new CredentialController(credentialStore);
    }

    public DeviceFlow startAuthenticate(Consumer<Optional<Twitch>> consumer) {
        var flow = controller.startAuthenticate(result -> {
            if (result) {
                controller.disposeAuthenticate();
                var twitch = createAppClient(controller.getCredential());
                consumer.accept(Optional.of(twitch));
            } else {
                consumer.accept(Optional.empty());
            }
        });
        return new DeviceFlow(flow.userCode(), flow.verificationURL());
    }

    public Optional<Twitch> load() {
        if (!controller.isAuthorized()) {
            return Optional.empty();
        }

        OAuth2Credential credential;

        // 資格情報を検証して適宜リフレッシュする
        if (controller.validateToken()) {

            // 有効だった場合はその資格情報を使う
            credential = (OAuth2Credential) controller.getCredentialManager()
                    .getCredentials().getFirst();

        } else {

            // 無効だった場合はリフレッシュする
            LOGGER.info("refresh token {}", controller.getCredentialManager().getCredentials().getFirst());
            credential = controller.refreshToken();

            // リフレッシュに失敗したときは資格情報を空で返す
            if (credential == null) {
                LOGGER.info("failed to refresh token");
                return Optional.empty();
            } else {
                LOGGER.info("refreshed {}", credential);
            }
        }

        return Optional.of(createAppClient(credential));
    }

    private Twitch createAppClient(OAuth2Credential credential) {
        var credentialManager = controller.getCredentialManager();
        var client = TwitchClientBuilder.builder()
                .withCredentialManager(credentialManager)
                .withDefaultAuthToken(credential)
                .withEnableHelix(true)
                .withChatAccount(credential)
                .withEnableChat(true)
                .build();
        return new Twitch(credential, credentialStore, client);
    }

}
