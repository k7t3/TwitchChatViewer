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
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.TwitchChat;

import java.io.Closeable;
import java.io.IOException;

public class Twitch implements Closeable {

    private final OAuth2Credential credential;
    private final TwitchClient client;
    private final CredentialStore credentialStore;

    private TwitchAPI twitchAPI;

    Twitch(
            OAuth2Credential credential,
            CredentialStore credentialStore,
            TwitchClient client
    ) {
        this.credentialStore = credentialStore;
        this.credential = credential;
        this.client = client;
    }

    public TwitchAPI getTwitchAPI() {
        if (twitchAPI == null) twitchAPI = new TwitchAPI(this);
        return twitchAPI;
    }

    void updateCredential(OAuth2Credential newOne) {
        getCredential().updateCredential(newOne);
    }

    CredentialStore getCredentialStore() {
        return credentialStore;
    }

    private OAuth2Credential getCredential() {
        return credential;
    }

    public String getAccessToken() {
        return getCredential().getAccessToken();
    }

    public String getUserId() {
        return getCredential().getUserId();
    }

    public String getUserName() {
        return getCredential().getUserName();
    }

    /**
     * ドメインパッケージでの使用に限りたい。
     */
    public TwitchClient getClient() {
        return client;
    }

    public TwitchChat getChat() {
        return client.getChat();
    }

    public void logout() {
        if (twitchAPI != null) {
            try {
                twitchAPI.close();
            } catch (IOException ignored) {
            }
            twitchAPI = null;
        }
        client.close();

        var credentialController = new CredentialController(credentialStore);
        try {
            credentialController.revokeToken();
        } finally {
            credentialController.disposeAuthenticate();
        }

        credentialStore.clearCredentials();
    }

    @Override
    public void close() {
        if (twitchAPI != null) {
            try {
                twitchAPI.close();
            } catch (IOException ignored) {
            }
        }

        client.close();
    }

}
