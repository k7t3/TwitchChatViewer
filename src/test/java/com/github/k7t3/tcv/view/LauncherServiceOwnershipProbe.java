package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.main.MainView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;

/** 標準サービスの保存先と終了処理を隔離して、既定Launcherを2回実行する。 */
public final class LauncherServiceOwnershipProbe {
    public static final class IsolatedPreferences implements java.util.prefs.PreferencesFactory {
        private final Preferences user = new MemoryPreferences();
        private final Preferences system = new MemoryPreferences();
        public Preferences userRoot() { return user; }
        public Preferences systemRoot() { return system; }
    }

    public static void main(String[] arguments) throws Exception {
        var root = Path.of(arguments[0]);
        Files.createDirectories(root.resolve("tmp"));
        System.setProperty("user.home", root.toString());
        System.setProperty("java.io.tmpdir", root.resolve("tmp").toString());
        System.setProperty("java.util.prefs.PreferencesFactory", IsolatedPreferences.class.getName());
        var ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            // 認証を開始するViewとエラー表示だけを、FXスレッドの終了まで差し替える。
            mockConstruction(MainView.class);
            mockConstruction(Alert.class, (alert, context) ->
                    when(alert.showAndWait()).thenReturn(Optional.empty()));
            ready.countDown();
        });
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("toolkit did not start");
            for (int index = 1; index <= 2; index++) {
                var launcher = new Launcher();
                launcher.init();
                var shownAndClosed = new CompletableFuture<Boolean>();
                Platform.runLater(() -> {
                    try {
                        var stage = new Stage();
                        launcher.start(stage);
                        var shown = stage.isShowing();
                        if (shown) launcher.stop();
                        shownAndClosed.complete(shown && !stage.isShowing());
                    } catch (Throwable failure) { shownAndClosed.completeExceptionally(failure); }
                });
                if (!shownAndClosed.get(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("default launcher " + index + " reused an unavailable application service");
                }
            }
            System.out.println("two default launchers released their own application services");
        } finally { Platform.exit(); }
    }
}
