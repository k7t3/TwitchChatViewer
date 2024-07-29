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

package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CredentialController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialController.class);

    private static final String CLIENT_ID = "m2xt95z7dxg78rys5ds3ugc7klxjwn";
    private static final String REDIRECT_URL = null;

    private final TwitchIdentityProvider identityProvider = new TwitchIdentityProvider(CLIENT_ID, null, REDIRECT_URL);

    private final DeviceFlowController authController;

    private final CredentialManager credentialManager;

    private final boolean authorized;

    private OAuth2Credential credential;

    public CredentialController(CredentialStore credentialStore) {
        var flowExecutor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        authController = new DeviceFlowController(flowExecutor, 0);
        credentialManager = new CredentialManager(credentialStore, authController);
        credentialManager.registerIdentityProvider(identityProvider);
        authorized = !credentialManager.getCredentials().isEmpty();

        if (authorized) {
            credential = (OAuth2Credential) credentialManager.getCredentials().getFirst();
        }
    }

    /**
     * すでに認可トークンが生成されている。ただし有効であるかどうかは判定しない。
     * @return すでに認可トークンが生成されていた場合はTrue
     */
    public boolean isAuthorized() {
        return authorized;
    }

    /**
     * 認可トークンが生成済みだった場合、有効であるか検証する
     * @return 生成済みの認可トークンが有効であるかを判定した結果
     */
    public boolean validateToken() {
        if (!authorized) return false;
        return identityProvider.isCredentialValid(credential).orElse(false);
    }

    public OAuth2Credential refreshToken() {
        try {
            var refreshed = identityProvider.refreshCredential(credential).orElse(null);
            if (refreshed != null) {
                credentialManager.getCredentials().clear();
                credentialManager.addCredential(identityProvider.getProviderName(), refreshed);
                credentialManager.save();

                this.credential = refreshed;
            }
            return refreshed;
        } catch (RuntimeException e) {
            // リフレッシュに失敗した場合
            LOGGER.warn("failed to refresh", e);
            return null;
        }
    }

    public void revokeToken() {
        if (!authorized) return;
        identityProvider.revokeCredential(credential);
    }

    public OAuth2Credential getCredential() {
        return credential;
    }

    public CredentialManager getCredentialManager() {
        return credentialManager;
    }

    public DeviceFlow startAuthenticate(Consumer<Boolean> callback) {
        List<Object> scopes = List.of(
                TwitchScopes.HELIX_USER_FOLLOWS_READ,
                TwitchScopes.CHAT_READ
        );
        if (credentialManager.getCredentials() != null) {
            credentialManager.getCredentials().clear();
        }
        var auth = authController.startOAuth2DeviceAuthorizationGrantType(identityProvider, scopes, r -> callback(r, callback));
        return new DeviceFlow(auth.getUserCode(), auth.getVerificationUri(), auth.getCompleteUri());
    }

    /**
     * 認証結果のコールバック
     * @param response レスポンス
     */
    private void callback(DeviceTokenResponse response, Consumer<Boolean> callback) {
        var credential = response.getCredential();
        if (credential != null) {
            credentialManager.save();
            this.credential = credential;
        }
        callback.accept(credential != null);
    }

    public void disposeAuthenticate() {
        authController.close();
    }

    public record DeviceFlow(String userCode, String verificationURL, String completeURL) {}

}
