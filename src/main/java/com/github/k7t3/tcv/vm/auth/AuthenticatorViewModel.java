package com.github.k7t3.tcv.vm.auth;

import com.github.k7t3.tcv.domain.auth.Authenticator;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.service.FXTask;
import com.github.k7t3.tcv.vm.service.TaskWorker;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class AuthenticatorViewModel implements ViewModel, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorViewModel.class);

    private Authenticator authenticator;

    private final ReadOnlyBooleanWrapper initialized = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyStringWrapper authUri = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    public AuthenticatorViewModel() {
    }

    private record Pair(TwitchClient client, OAuth2Credential credential) {}

    /**
     * 既存の資格情報ロードする。
     * 失効している場合は適宜リフレッシュする。
     * 認証されていないときは値としてNullが返る。
     * @return 資格情報
     */
    public FXTask<?> loadCredentialAsync() {
        var task = FXTask.task(() -> {
            authenticator = new Authenticator();
            var authorized = authenticator.isAuthorized();

            if (!authorized) {
                LOGGER.info("done credential is empty");
                return null;
            }

            // 資格情報を検証して適宜リフレッシュする
            OAuth2Credential credential;
            if (authenticator.validateToken()) {
                // 有効だった場合はその資格情報を使う
                credential = (OAuth2Credential) authenticator.getCredentialManager()
                        .getCredentials().getFirst();
            } else {
                // 無効だった場合はリフレッシュする
                LOGGER.info("refresh token {}", authenticator.getCredentialManager().getCredentials().getFirst());
                credential = authenticator.refreshToken();

                // リフレッシュに失敗したときは資格情報を空で返す
                if (credential == null) {
                    LOGGER.info("failed to refresh token");
                    return null;
                } else {
                    LOGGER.info("refreshed {}", credential);
                }
            }

            var client = createClient(credential);

            return new Pair(client, credential);
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            initialized.set(true);
            var pair = task.getValue();
            if (pair != null) {
                done(pair.credential, pair.client);
            }
        });
        LOGGER.info("load credential store");
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<String> startAuthenticateAsync() {
        if (!initialized.get()) {
            throw new IllegalStateException("first, do loadCredentialAsync");
        }

        LOGGER.info("start authenticate");

        var task = FXTask.task(() -> authenticator.authenticate(this::handleToken));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            var uri = task.getValue();
            // 認証URI
            authUri.set(uri);
        });

        TaskWorker.getInstance().submit(task);

        return task;
    }

    private void handleToken(DeviceTokenResponse response) {
        var credential = response.getCredential();
        if (credential != null) {
            var client = createClient(credential);
            Platform.runLater(() -> done(credential, client));
            authenticator.close();
            return;
        }

        var errorType = response.getError();
        if (errorType == null) return;

        error.set(errorType.name());
        LOGGER.error(errorType.name());
    }

    private TwitchClient createClient(OAuth2Credential credential) {
        return TwitchClientBuilder.builder()
                .withCredentialManager(authenticator.getCredentialManager())
                .withDefaultAuthToken(credential)
                .withChatAccount(credential)
                .withEnableHelix(true)
                .withEnableChat(true)
                .build();
    }

    public void openAuthUri() {
        var uri = getAuthUri();
        if (uri == null) return;

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return;

            try {
                desktop.browse(new URI(uri));
            } catch (IOException e) {
                LOGGER.error("NOT SUPPORTED OPEN BROWSE", e);
            } catch (URISyntaxException e) {
                LOGGER.error("ILLEGAL URL?", e);
            }
        });
        TaskWorker.getInstance().submit(task);
    }

    public void clipAuthUri() {
        var uri = getAuthUri();
        if (uri == null) return;

        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, uri));
    }

    private void done(OAuth2Credential credential, TwitchClient client) {
        var helper = AppHelper.getInstance();
        helper.update(credential, client);

        authorized.set(true);

        LOGGER.info("done authentication {}", credential);
    }

    @Override
    public void close() {
        authenticator.close();
    }

    // ******************** properties ********************
    public ReadOnlyStringProperty authUriProperty() { return authUri.getReadOnlyProperty(); }
    public String getAuthUri() { return authUri.get(); }

    public ReadOnlyStringProperty errorProperty() { return error.getReadOnlyProperty(); }
    public String getError() { return error.get(); }

    public ReadOnlyBooleanProperty initializedProperty() { return initialized.getReadOnlyProperty(); }
    public boolean isInitialized() { return initialized.get(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean getAuthorized() { return authorized.get(); }

}
