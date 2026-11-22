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

package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.core.LoggerInitializer;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.app.secure.CredentialCipherService;
import com.github.k7t3.tcv.app.secure.KeyManagerFactory;
import com.github.k7t3.tcv.app.service.ApplicationShutdown;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.WindowBounds;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

public class Launcher extends Application {
    private final Logger log = LoggerFactory.getLogger(Launcher.class);
    private AppPreferences preferences;
    private AppHelper helper;
    private MainViewModel viewModel;
    private ApplicationWindow window;
    private final Runnable closeKeyManager;
    private final Consumer<Throwable> startupError;
    private final Runnable exit;
    private final Function<AppHelper, MainViewModel> modelFactory;
    private final WindowFactory windowFactory;
    private ApplicationShutdown shutdown;
    private LoggerInitializer.LoggingSession logging;
    private CredentialCipherService credentialCipher;
    private Throwable preparationFailure;
    private volatile Throwable startupFailure;
    private volatile Throwable startupException;
    private final CompletableFuture<Void> initializationCompleted = new CompletableFuture<>();
    private final CompletableFuture<Void> startupCompleted = new CompletableFuture<>();
    private volatile boolean initializationStarted;
    private volatile boolean initialized;
    private WindowBounds savedBounds;
    private boolean forcedShutdown;
    private volatile boolean started;
    private volatile Thread startingThread;

    @FunctionalInterface
    interface WindowFactory {
        ApplicationWindow create(Stage stage, MainViewModel model, AppHelper helper, AppPreferences preferences, WindowBounds savedBounds,
                                 Runnable hidden, Consumer<Throwable> failed);
    }

    public Launcher() { this(null, null, () -> {},
            failure -> ExceptionHandler.handle(null, failure), Platform::exit); }

    Launcher(AppPreferences preferences, AppHelper helper, Runnable closeKeyManager,
             Consumer<Throwable> startupError, Runnable exit) {
        this(preferences, helper, closeKeyManager, startupError, exit, null, null);
    }

    Launcher(AppPreferences preferences, AppHelper helper, Runnable closeKeyManager, Consumer<Throwable> startupError,
             Runnable exit, Function<AppHelper, MainViewModel> modelFactory, WindowFactory windowFactory) {
        this.preferences = preferences;
        this.helper = helper;
        this.closeKeyManager = closeKeyManager;
        this.startupError = startupError;
        this.exit = exit;
        this.modelFactory = modelFactory != null ? modelFactory : this::createModel;
        this.windowFactory = windowFactory != null ? windowFactory : this::createWindow;
    }

    private MainViewModel createModel(AppHelper owner) {
        return MainModelFactory.create(owner, this::reportOperationFailure);
    }

    private void reportOperationFailure(Throwable failure) {
        ExceptionHandler.handle(helper.getPrimaryStage(), failure);
    }

    private ApplicationWindow createWindow(Stage stage, MainViewModel model, AppHelper owner, AppPreferences settings,
                                           WindowBounds bounds, Runnable hidden, Consumer<Throwable> failed) {
        credentialCipher = KeyManagerFactory.create();
        var credentials = new PreferencesCredentialStorage(settings.getPreferences(),
                credentialCipher::encrypt, credentialCipher::decrypt,
                com.github.k7t3.tcv.app.core.OS.current().getApplicationDirectory().resolve("credentials.lock"),
                settings.settingsLockFile().orElse(null));
        return new MainWindow(stage, model, owner, settings, bounds, hidden, failed, credentials);
    }

    @Override
    public void init() {
        synchronized (this) {
            if (initializationStarted || shutdown != null) throw new IllegalStateException("application initialization already ended");
            initializationStarted = true;
        }
        try {
            if (Platform.isFxApplicationThread()) throw new IllegalStateException("initialize storage outside the FX thread");
            Platform.setImplicitExit(false);
            logging = LoggerInitializer.initialize();
            if (preferences == null) preferences = new AppPreferences();
            if (helper == null) helper = new AppHelper(this::reportOperationFailure);
            preferences.load();
            var general = preferences.getGeneralPreferences();
            var file = new UserDataFile(general.getUserDataFilePath());
            helper.setUserDataFile(file);
            var version = file.connectDatabase(general.getDatabaseVersion());
            helper.recoverSettings(preferences);
            preferences.load();
            general.setDatabaseVersion(version);
            // 画面を構築する前に、背景起動で受け付けた版更新の実保存・失敗を確認する。
            if (preferences.settingsLockFile().isPresent()) preferences.save();
            savedBounds = helper.getWindowBoundsService().getBounds("main");
        } catch (IOException | RuntimeException | Error failure) {
            recordStartupFailure(failure);
        } finally {
            // stop が待つ間に同じ monitor を要求しない。
            initialized = true;
            initializationCompleted.complete(null);
        }
    }

    @Override
    public void start(Stage stage) {
        if (!initialized) throw new IllegalStateException("application is not initialized");
        if (started || shutdown != null) throw new IllegalStateException("application startup already ended");
        started = true;
        startingThread = Thread.currentThread();
        try {
            if (startupFailure != null) { requestShutdown(); return; }
            helper.setPrimaryStage(stage);
            var general = preferences.getGeneralPreferences();
            var createdModel = modelFactory.apply(helper);
            if (shutdown != null) { createdModel.close(); return; }
            viewModel = createdModel;
            viewModel.getChannelListViewModel().bindGeneralPreferences(general);
            if (shutdown != null) return;
            viewModel.getChatContainer().bindChatPreferences(preferences.getChatPreferences());
            if (shutdown != null) return;
            // show より先に所有し、FXML やテーマ適用の途中失敗も回収する。
            var createdWindow = windowFactory.create(stage, viewModel, helper, preferences, savedBounds, this::requestShutdown, this::failStartup);
            if (shutdown != null) { createdWindow.close(); return; }
            window = createdWindow;
            window.show();
        } catch (RuntimeException | Error failure) { failStartup(failure); }
        finally {
            startingThread = null;
            startupCompleted.complete(null);
        }
    }

    private void failStartup(Throwable failure) {
        recordStartupFailure(failure);
        requestShutdown();
    }

    private void recordStartupFailure(Throwable failure) {
        if (startupFailure == null) {
            startupFailure = failure;
            startupException = failure instanceof IOException io ? new UncheckedIOException(io) : failure;
        } else if (startupException != failure) startupException.addSuppressed(failure);
    }

    /** FX スレッドからの終了要求。部分起動の終了も同じ完了を共有する。 */
    public synchronized CompletionStage<Void> requestShutdown() { return shutdown(false).request(); }

    private ApplicationShutdown shutdown(boolean forced) {
        if (shutdown == null) {
            forcedShutdown = forced;
            shutdown = new ApplicationShutdown(
                    command -> Thread.ofVirtual().name("TCV-Shutdown").start(command),
                    forced ? Runnable::run : Platform::runLater,
                    this::prepareShutdown, this::releaseResources, forced ? () -> {} : () -> CloseActions.runAll(
                            () -> { if (startupFailure != null) startupError.accept(startupFailure); }, exit));
            shutdown.request().whenComplete((ignored, failure) -> {
                if (failure != null) log.error("application shutdown failed", failure);
            });
        }
        return shutdown;
    }

    @Override
    public void stop() {
        final ApplicationShutdown current;
        synchronized (this) { current = shutdown(true); }
        // 構築の中から再入したstopは、同じスレッドで戻る生成処理を待たない。
        if (startingThread == Thread.currentThread()) return;
        try { current.resourcesReleased().toCompletableFuture().join(); }
        catch (CompletionException failure) {
            if (forcedShutdown) rethrow(failure.getCause());
        }
    }

    private void prepareShutdown() {
        var ownedWindow = window;
        var ownedModel = viewModel;
        window = null;
        viewModel = null;
        var failure = startupException;
        try {
            CloseActions.runAll(() -> { if (failure != null) rethrow(failure); },
                    () -> { if (ownedWindow != null) ownedWindow.close(); },
                    () -> { if (ownedModel != null) ownedModel.close(); },
                    () -> { if (helper != null && (!initializationStarted || initialized)) helper.prepareShutdown(); });
        } catch (RuntimeException | Error error) {
            preparationFailure = error;
            throw error;
        }
    }

    private void releaseResources() {
        try {
            CloseActions.runAll(() -> {
                        if (initializationStarted) initializationCompleted.join();
                        if (started) startupCompleted.join();
                    }, () -> {
                        if (preparationFailure != null) rethrow(preparationFailure);
                    }, () -> {
                        if (startupException != null) rethrow(startupException);
                    }, () -> { if (helper != null) helper.close(); },
                    () -> { if (preferences != null) preferences.close(); }, this::closeCredentialCipher, closeKeyManager);
        } catch (RuntimeException | Error failure) {
            // 起動・解放の診断をログへ書いてから、最後にログ自身を解放する。
            CloseActions.runAll(() -> rethrow(failure),
                    () -> log.error("application resource release failed", failure), this::closeLogging);
        }
        closeLogging();
    }

    private void closeLogging() {
        var owned = logging;
        logging = null;
        if (owned != null) owned.close();
    }

    private void closeCredentialCipher() {
        var owned = credentialCipher;
        credentialCipher = null;
        if (owned != null) owned.close();
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
        if (failure instanceof IOException error) throw new UncheckedIOException(error);
        throw new RuntimeException(failure);
    }
}
