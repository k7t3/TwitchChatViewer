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

    public record DeviceFlow(String userCode, String verificationURL, String completeURL) {}
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
        return new DeviceFlow(flow.userCode(), flow.verificationURL(), flow.completeURL());
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
            LOGGER.info("refresh a token");
            credential = controller.refreshToken();

            // リフレッシュに失敗したときは資格情報を空で返す
            if (credential == null) {
                LOGGER.info("failed to refresh token");
                return Optional.empty();
            } else {
                LOGGER.info("refreshed access token");
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
