package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

public class AuthenticatorViewModel implements ViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorViewModel.class);

    private TwitchLoader twitchLoader;

    private final ReadOnlyBooleanWrapper initialized = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyStringWrapper userCode = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper authUri = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    public AuthenticatorViewModel() {
    }

    public FXTask<Optional<Twitch>> loadClientAsync() {
        var preferences = AppPreferences.getInstance();

        twitchLoader = new TwitchLoader(preferences.getPreferences());
        var task = FXTask.task(() -> twitchLoader.load());
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            initialized.set(true);
            task.getValue().ifPresent(this::done);
        });
        LOGGER.info("load credential store");
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<?> startAuthenticateAsync() {
        if (!initialized.get()) {
            throw new IllegalStateException("first, do loadCredentialAsync");
        }

        LOGGER.info("start authenticate");

        var task = FXTask.task(
                () -> twitchLoader.startAuthenticate(
                        twitch -> twitch.ifPresent(value -> Platform.runLater(() -> done(value)))
                )
        );
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            var deviceFlow = task.getValue();
            // 認証URI
            authUri.set(deviceFlow.verificationURL());
            // ユーザーコード
            userCode.set(deviceFlow.userCode());
        });

        TaskWorker.getInstance().submit(task);

        return task;
    }

    private void done(Twitch twitch) {
        var helper = AppHelper.getInstance();
        helper.setTwitch(twitch);

        authorized.set(true);
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

    // ******************** properties ********************
    public ReadOnlyStringProperty authUriProperty() { return authUri.getReadOnlyProperty(); }
    public String getAuthUri() { return authUri.get(); }

    public ReadOnlyStringProperty userCodeProperty() { return userCode.getReadOnlyProperty(); }
    public String getUserCode() { return userCode.get(); }

    public ReadOnlyStringProperty errorProperty() { return error.getReadOnlyProperty(); }
    public String getError() { return error.get(); }

    public ReadOnlyBooleanProperty initializedProperty() { return initialized.getReadOnlyProperty(); }
    public boolean isInitialized() { return initialized.get(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean getAuthorized() { return authorized.get(); }

}
