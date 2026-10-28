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

package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Twitchユーザー認証用ViewModel
 * <p>
 *     OAuth Device Authorization Flowを使用して認証する。
 * </p>
 * <p>
 *     CredentialStoreにアクセストークンが保管されている場合、
 *     {@link #loadClientAsync()}メソッドで{@link Twitch}インスタンスを取得できる。
 * </p>
 * <p>
 *     トークンが有効でない場合は{@link #startAuthenticateAsync()}メソッドで
 *     認証フローを開始する。
 * </p>
 */
public class AuthenticatorViewModel extends AbstractViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorViewModel.class);

    private static final int QRCODE_WIDTH = 480;
    private static final int QRCODE_HEIGHT = 480;

    private TwitchLoader twitchLoader;
    private final Object authenticationLock = new Object();
    private Object attempt;
    private Twitch pendingClient;

    private final ReadOnlyBooleanWrapper initialized = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper userCode = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper authUri = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper();
    private final ReadOnlyObjectWrapper<Image> qrcode = new ReadOnlyObjectWrapper<>();
    private final Supplier<TwitchLoader> loaderFactory;
    private final Consumer<Twitch> authenticated;
    private final Executor executor;
    private final BrowserActions browsers;
    private final List<FXTask<?>> tasks = new ArrayList<>();
    private final Set<Object> operations = new HashSet<>();
    private final CompletableFuture<Void> released = new CompletableFuture<>();
    private final CompletionStage<Void> releasedResult = released.minimalCompletionStage();
    private boolean releasePrepared;
    private Throwable releaseFailure;

    /** 認証URLのブラウザ起動失敗は、呼び出し元の通知先へ渡す。 */
    public AuthenticatorViewModel(CredentialStore store, Consumer<Twitch> authenticated, Executor executor,
                                  Consumer<Throwable> failures) {
        this(() -> new TwitchLoader(store), authenticated, executor, failures);
    }

    AuthenticatorViewModel(Supplier<TwitchLoader> loaderFactory, Consumer<Twitch> authenticated, Executor executor,
                            Consumer<Throwable> failures) {
        this.loaderFactory = loaderFactory;
        this.authenticated = authenticated;
        this.executor = executor;
        browsers = new BrowserActions(executor, stage -> {
            var work = beginWork();
            stage.whenComplete((ignored, failure) -> finishWork(work, failure));
        }, failures);
    }

    /** 保存済み資格情報を確認し、利用できなければ Device Flow を開始する。実行中と終了後の呼び出しは無視する。 */
    public void beginAuthentication() {
        if (isClosed() || isBusy()) return;
        var load = loadClientAsync();
        load.onDone(result -> {
            if (!isClosed() && isInitialized() && result.isEmpty()) startAuthenticateAsync();
        });
    }

    public FXTask<Optional<Twitch>> loadClientAsync() {
        if (isClosed()) throw new IllegalStateException("authenticator is closed");
        if (isBusy()) throw new IllegalStateException("authentication is already running");
        resetAuthentication();
        error.set(null);
        busy.set(true);
        var token = new Object();
        synchronized (authenticationLock) {
            attempt = token;
        }
        var task = FXTask.task(() -> {
            var loader = loaderFactory.get();
            final boolean current;
            synchronized (authenticationLock) {
                current = isCurrent(token);
                if (current) twitchLoader = loader;
            }
            if (!current) { releaseLoader(loader); return Optional.<Twitch>empty(); }
            var result = loader.load();
            result.ifPresent(client -> retainClient(token, client));
            return result;
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            if (!isCurrent(token)) return;
            initialized.set(true);
            busy.set(false);
            task.getValue().ifPresent(client -> done(token, client));
        });
        LOGGER.info("load credential store");
        submit(task, token);
        return task;
    }

    public FXTask<?> startAuthenticateAsync() {
        if (isClosed()) throw new IllegalStateException("authenticator is closed");
        if (!initialized.get()) {
            throw new IllegalStateException("first, do loadCredentialAsync");
        }
        if (isBusy()) throw new IllegalStateException("authentication is already running");
        busy.set(true);

        LOGGER.info("start authenticate");

        final Object token;
        final TwitchLoader loader;
        synchronized (authenticationLock) {
            token = attempt;
            loader = twitchLoader;
        }
        var task = FXTask.task(() -> loader.startAuthenticate(result -> {
            if (result.isEmpty()) {
                Platform.runLater(() -> failAuthentication(token));
                return;
            }
            var client = result.orElseThrow();
            if (retainClient(token, client)) Platform.runLater(() -> done(token, client));
        }));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            if (!isCurrent(token)) return;
            var deviceFlow = task.getValue();
            // 認証URI
            authUri.set(deviceFlow.completeURL());
            // ユーザーコード
            userCode.set(deviceFlow.userCode());

            // QRコードを生成する
            generateQRCode(token, deviceFlow.verificationURL());
        });

        submit(task, token);

        return task;
    }

    private void generateQRCode(Object token, String url) {
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
        t.onDone(() -> {
            if (isCurrent(token)) qrcode.set(t.getValue());
        });
        submit(t, token);
    }

    private boolean isCurrent(Object token) {
        synchronized (authenticationLock) {
            return !isClosed() && token != null && attempt == token;
        }
    }

    private boolean retainClient(Object token, Twitch client) {
        synchronized (authenticationLock) {
            if (isCurrent(token) && pendingClient == null) {
                pendingClient = client;
                return true;
            }
        }
        releaseClient(client);
        return false;
    }

    private void done(Object token, Twitch twitch) {
        synchronized (authenticationLock) {
            if (!isCurrent(token) || pendingClient != twitch) return;
            pendingClient = null;
        }
        busy.set(false);
        try {
            authenticated.accept(twitch);
        } catch (RuntimeException | Error failure) {
            try { releaseClient(twitch); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            LOGGER.error("authenticated client was not accepted", failure);
            failAuthentication(token);
            return;
        }

        // ログインイベントを発行
        publish(new LoginEvent());
    }

    private void failAuthentication(Object token) {
        if (!isCurrent(token)) return;
        resetAuthentication();
        error.set(Resources.getString("auth.error.failed"));
    }

    private void submit(FXTask<?> task, Object token) {
        tasks.add(task);
        task.setFinally(() -> tasks.remove(task));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> failAuthentication(token));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, e -> {
            if (isCurrent(token)) resetAuthentication();
        });
        var operation = beginWork();
        try {
            executor.execute(() -> {
                try { task.run(); }
                finally { finishWork(operation, null); }
            });
        } catch (RuntimeException | Error rejected) {
            finishWork(operation, null);
            CloseActions.runAll(() -> failAuthentication(token), task::cancel);
            if (!(rejected instanceof RejectedExecutionException)) rethrow(rejected);
        }
    }

    @Override
    protected void onClose() {
        try { CloseActions.runAll(browsers::close, this::resetAuthentication); }
        finally {
            synchronized (authenticationLock) {
                releasePrepared = true;
                completeReleaseIfReady();
            }
        }
    }

    @Override
    protected void onLoggedOut() {
        resetAuthentication();
        error.set(null);
    }

    private void resetAuthentication() {
        browsers.cancelAll();
        final TwitchLoader loader;
        final Twitch client;
        synchronized (authenticationLock) {
            attempt = null;
            loader = twitchLoader;
            twitchLoader = null;
            client = pendingClient;
            pendingClient = null;
        }
        var pending = List.copyOf(tasks);
        tasks.clear();
        CloseActions.runAll(
                () -> CloseActions.runAll(pending.stream().map(task -> (Runnable) task::cancel).toArray(Runnable[]::new)),
                () -> { if (loader != null) requestLoaderClose(loader); },
                () -> { if (client != null) releaseClient(client); },
                () -> { if (loader != null) executeCleanup(loader::awaitClosed); },
                () -> {
                    initialized.set(false);
                    busy.set(false);
                    authUri.set(null);
                    userCode.set(null);
                    qrcode.set(null);
                });
    }

    private void releaseClient(Twitch client) {
        executeCleanup(client::close);
    }

    private void releaseLoader(TwitchLoader loader) {
        CloseActions.runAll(() -> requestLoaderClose(loader), () -> executeCleanup(loader::awaitClosed));
    }

    private void requestLoaderClose(TwitchLoader loader) {
        try { loader.close(); }
        catch (RuntimeException | Error failure) {
            synchronized (authenticationLock) { rememberReleaseFailure(failure); }
            throw failure;
        }
    }

    private Object beginWork() {
        synchronized (authenticationLock) {
            if (released.isDone()) throw new IllegalStateException("authentication resources already released");
            var operation = new Object();
            operations.add(operation);
            return operation;
        }
    }

    private void finishWork(Object operation, Throwable failure) {
        synchronized (authenticationLock) {
            if (!operations.remove(operation)) return;
            if (failure != null) rememberReleaseFailure(failure);
            completeReleaseIfReady();
        }
    }

    private void rememberReleaseFailure(Throwable failure) {
        if (releaseFailure == null) releaseFailure = failure;
        else if (releaseFailure != failure) releaseFailure.addSuppressed(failure);
    }

    private void completeReleaseIfReady() {
        if (!releasePrepared || !operations.isEmpty()) return;
        if (releaseFailure == null) released.complete(null);
        else released.completeExceptionally(releaseFailure);
    }

    private void executeCleanup(Runnable action) {
        var operation = beginWork();
        Runnable cleanup = () -> {
            Throwable failure = null;
            try { action.run(); }
            catch (RuntimeException | Error error) { failure = error; LOGGER.error("authentication resource cleanup failed", error); }
            finally { finishWork(operation, failure); }
        };
        try { executor.execute(cleanup); }
        catch (RuntimeException | Error rejected) {
            if (!(rejected instanceof RejectedExecutionException)) {
                synchronized (authenticationLock) { rememberReleaseFailure(rejected); }
            }
            try { Thread.ofVirtual().name("TCV-Authentication-Cleanup").start(cleanup); }
            catch (RuntimeException | Error unavailable) {
                unavailable.addSuppressed(rejected);
                synchronized (authenticationLock) { rememberReleaseFailure(unavailable); }
                cleanup.run();
            }
        }
    }

    /** 画面終了後、取消済みタスクの実終了と全リソース解放が終わった時点で完了する。 */
    public CompletionStage<Void> resourcesReleased() { return releasedResult; }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }

    public void openAuthUri() {
        if (isClosed()) return;
        var uri = getAuthUri();
        if (uri != null) browsers.open(uri);
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

    /** キュー待機、資格情報の読み込み、ユーザーの認可待ちを含む認証中の状態。 */
    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public boolean isBusy() { return busy.get(); }

    public ReadOnlyObjectProperty<Image> qrcodeProperty() { return qrcode.getReadOnlyProperty(); }
    public Image getQRCode() { return qrcode.get(); }
}
