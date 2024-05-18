package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.QRCode;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
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

    private static final int QRCODE_WIDTH = 480;
    private static final int QRCODE_HEIGHT = 480;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorViewModel.class);

    private TwitchLoader twitchLoader;

    private final ReadOnlyBooleanWrapper initialized = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyStringWrapper userCode = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper authUri = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyObjectWrapper<Image> qrcode = new ReadOnlyObjectWrapper<>();

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
        task.runAsync();
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

            // QRコードを生成する
            generateQRCode(deviceFlow.verificationURL());
        });

        task.runAsync();

        return task;
    }

    private void generateQRCode(String url) {
        var t = FXTask.task(() -> {
            var writer = new QRCodeWriter();
            var matrix = writer.encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    QRCODE_WIDTH,
                    QRCODE_HEIGHT,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            );
            var bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            return SwingFXUtils.toFXImage(bufferedImage, null);
        });
        t.setSucceeded(() -> qrcode.set(t.getValue()));
        t.runAsync();
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
        task.runAsync();
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

    public ReadOnlyObjectProperty<Image> qrcodeProperty() { return qrcode.getReadOnlyProperty(); }
    public Image getQRCode() { return qrcode.get(); }

}
