package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class Authenticator implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Authenticator.class);

    private static final String CLIENT_ID = "0wy9r8njn27zf1nzno22ib60i4xj77";
    private static final String REDIRECT_URL = "http://localhost:3000";

    private final TwitchIdentityProvider identityProvider = new TwitchIdentityProvider(CLIENT_ID, null, REDIRECT_URL);

    private final DeviceFlowController authController;

    private final CredentialManager credentialManager;

    private final boolean authorized;

    public Authenticator() {
        var flowExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        authController = new DeviceFlowController(flowExecutor, 0);
        credentialManager = new CredentialManager(new CredentialFileStorage(Path.of("userinfo")), authController);
        credentialManager.registerIdentityProvider(identityProvider);
        authorized = !credentialManager.getCredentials().isEmpty();
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
        var credential = credentialManager.getCredentials().getFirst();
        return identityProvider.isValid(credential);
    }

    public OAuth2Credential refreshToken() {
        var credential = (OAuth2Credential) credentialManager.getCredentials().getFirst();

        try {
            var refreshed = identityProvider.refreshCredential(credential).orElse(null);
            if (refreshed != null) {
                credentialManager.getCredentials().clear();
                credentialManager.addCredential(identityProvider.getProviderName(), credential);
                credentialManager.save();
            }
            return refreshed;
        } catch (RuntimeException e) {
            // リフレッシュに失敗した場合
            LOGGER.warn("failed to refresh", e);
            return null;
        }
    }

    public CredentialManager getCredentialManager() {
        return credentialManager;
    }

    /**
     * デバイス認証URIを返す
     * @param consumer 認証結果を受け取るコールバック
     * @return デバイス認証URI
     */
    public String authenticate(Consumer<DeviceTokenResponse> consumer) {
        List<Object> scopes = List.of(
                TwitchScopes.CHAT_READ,
                TwitchScopes.HELIX_USER_FOLLOWS_READ
        );
        if (credentialManager.getCredentials() != null) {
            credentialManager.getCredentials().clear();
        }
        var request = authController.startOAuth2DeviceAuthorizationGrantType(identityProvider, scopes, r -> callback(r, consumer));
        return request.getCompleteUri();
    }

    /**
     * 認証結果のコールバック
     * @param response レスポンス
     * @param callback 呼び出し元コールバック
     */
    private void callback(DeviceTokenResponse response, Consumer<DeviceTokenResponse> callback) {
        var credential = response.getCredential();
        if (credential != null) {
            credentialManager.save();
        }
        callback.accept(response);
    }

    @Override
    public void close() {
        authController.close();
    }

}
