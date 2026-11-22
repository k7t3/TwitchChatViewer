package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.LoggerInitializer;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;

/** 実Launcherの既定通知を、保存先を初期化する前の失敗から別JVMで実行する。 */
public final class LauncherDefaultErrorProbe {
    public static void main(String[] arguments) throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("toolkit did not start");
        var launcher = new Launcher();
        var failure = new InvalidCredentialException();
        var unexpectedApplications = new AtomicInteger();
        var displayed = new AtomicInteger();
        var unowned = new AtomicBoolean();
        var completed = new CompletableFuture<Void>();
        try {
            try (var initializer = mockStatic(LoggerInitializer.class)) {
                initializer.when(LoggerInitializer::initialize).thenThrow(failure);
                launcher.init();
            }
            Platform.runLater(() -> {
                try {
                    var anotherWindow = new Stage();
                    anotherWindow.setScene(new Scene(new StackPane(), 320, 200));
                    anotherWindow.show();
                    // モックはこのJVMのFXスレッドがPlatform.exitで終了するまで有効にする。
                    mockConstruction(AppHelper.class, (application, context) -> unexpectedApplications.incrementAndGet());
                    mockConstruction(Alert.class, (alert, context) ->
                            when(alert.showAndWait()).thenAnswer(call -> {
                                displayed.incrementAndGet();
                                unowned.set(mockingDetails(alert).getInvocations().stream()
                                        .noneMatch(invocation -> invocation.getMethod().getName().equals("initOwner")));
                                return Optional.empty();
                            }));

                    launcher.start(new Stage());
                    launcher.requestShutdown().whenComplete((ignored, shutdownFailure) -> completed.complete(null));
                } catch (Throwable unexpected) { completed.completeExceptionally(unexpected); }
            });
            completed.get(10, TimeUnit.SECONDS);
            if (displayed.get() != 1 || !unowned.get() || unexpectedApplications.get() != 0) {
                throw new AssertionError("startup error acquired a global application: applications=" + unexpectedApplications.get()
                        + ", unowned=" + unowned.get() + ", dialogs=" + displayed.get());
            }
            System.out.println("startup failure remained unowned without acquiring another application");
        } finally { Platform.exit(); }
    }
}
