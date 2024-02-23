package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.api.IStorageBackend;
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

    private static final String CLIENT_ID = "0wy9r8njn27zf1nzno22ib60i4xj77";
    private static final String REDIRECT_URL = "http://localhost:3000";

    private final TwitchIdentityProvider identityProvider = new TwitchIdentityProvider(CLIENT_ID, null, REDIRECT_URL);

    private final DeviceFlowController authController;

    private final CredentialManager credentialManager;

    private final boolean authorized;

    private OAuth2Credential credential;

    public CredentialController(IStorageBackend backend) {
        var flowExecutor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        authController = new DeviceFlowController(flowExecutor, 0);
        credentialManager = new CredentialManager(backend, authController);
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

    public OAuth2Credential getCredential() {
        return credential;
    }

    public CredentialManager getCredentialManager() {
        return credentialManager;
    }

    public DeviceFlow startAuthenticate(Consumer<Boolean> callback) {
        List<Object> scopes = List.of(
                TwitchScopes.HELIX_USER_FOLLOWS_READ
        );
        if (credentialManager.getCredentials() != null) {
            credentialManager.getCredentials().clear();
        }
        var auth = authController.startOAuth2DeviceAuthorizationGrantType(identityProvider, scopes, r -> callback(r, callback));
        return new DeviceFlow(auth.getUserCode(), auth.getCompleteUri());
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

    public record DeviceFlow(String userCode, String verificationURL) {}

}
