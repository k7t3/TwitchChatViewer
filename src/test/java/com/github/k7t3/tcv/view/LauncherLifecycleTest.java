package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class LauncherLifecycleTest {

    @TempDir
    Path directory;

    @Test
    void forcedStopDuringShutdownWaitsForResourceRelease(FxRobot robot) throws Exception {
        var preferences = mock(AppPreferences.class);
        var helper = mock(AppHelper.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(helper).close();
        var launcher = new Launcher(preferences, helper, () -> {}, error -> {}, () -> {});
        var completion = new AtomicReference<CompletionStage<Void>>();
        try {
            robot.interact(() -> completion.set(launcher.requestShutdown()));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Platform.runLater(() -> {
                try {
                    launcher.stop();
                } finally {
                    stopped.countDown();
                }
            });

            assertThat(stopped.await(100, TimeUnit.MILLISECONDS)).isFalse();

            release.countDown();
            assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            if (completion.get() != null) completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void forcedStopStillReleasesResourcesAfterPreferenceFailure(FxRobot robot) {
        var preferences = mock(AppPreferences.class);
        var helper = mock(AppHelper.class);
        var keysClosed = new AtomicInteger();
        var failure = new IllegalStateException("preference save failed");
        doThrow(failure).when(preferences).close();
        var launcher = new Launcher(preferences, helper, keysClosed::incrementAndGet, error -> {}, () -> {});

        robot.interact(() -> assertThatThrownBy(launcher::stop).isSameAs(failure));

        verify(helper).close();
        assertThat(keysClosed.get()).isEqualTo(1);
    }

    @Test
    void javafxStopDoesNotRepeatCompletedShutdown(FxRobot robot) throws Exception {
        var preferences = mock(AppPreferences.class);
        var helper = mock(AppHelper.class);
        var keysClosed = new AtomicInteger();
        var launcher = new Launcher(preferences, helper, keysClosed::incrementAndGet, error -> {}, () -> {});
        var completion = new AtomicReference<CompletionStage<Void>>();
        robot.interact(() -> completion.set(launcher.requestShutdown()));
        completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

        robot.interact(launcher::stop);

        verify(preferences).close();
        verify(helper).close();
        assertThat(keysClosed.get()).isEqualTo(1);
    }

    @Test
    void requestedShutdownWaitsForResourcesOutsideFxThread(FxRobot robot) throws Exception {
        var preferences = mock(AppPreferences.class);
        var helper = mock(AppHelper.class);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var exits = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            started.countDown();
            release.await();
            return null;
        }).when(helper).close();
        var launcher = new Launcher(preferences, helper, () -> {}, error -> {}, exits::incrementAndGet);
        var completion = new AtomicReference<CompletionStage<Void>>();
        try {
            robot.interact(() -> completion.set(launcher.requestShutdown()));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            robot.interact(() -> assertThat(exits.get()).isZero());
            assertThat(completion.get().toCompletableFuture().isDone()).isFalse();

            release.countDown();
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(exits.get()).isEqualTo(1);
        } finally {
            release.countDown();
            if (completion.get() != null) completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stopReleasesResourcesWhenMainViewWasNeverCreated(FxRobot robot) {
        var preferences = mock(AppPreferences.class);
        var helper = mock(AppHelper.class);
        var keysClosed = new AtomicInteger();
        var launcher = new Launcher(preferences, helper, keysClosed::incrementAndGet, error -> {}, () -> {});

        robot.interact(() -> assertThatCode(launcher::stop).doesNotThrowAnyException());

        verify(preferences).close();
        verify(helper).close();
        assertThat(keysClosed.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseStartupFailureDoesNotContinueToViewConstruction(boolean sqliteFailure, FxRobot robot) throws Exception {
        var blocked = Files.writeString(directory.resolve("blocked"), "file");
        var preferences = mock(AppPreferences.class);
        var general = mock(GeneralPreferences.class);
        when(preferences.getGeneralPreferences()).thenReturn(general);
        var filePath = sqliteFailure ? directory : blocked.resolve("nested/userdata.db");
        when(general.getUserDataFilePath()).thenReturn(filePath.toString());
        when(general.getDatabaseVersion()).thenReturn(DatabaseVersion.EMPTY);
        var helper = mock(AppHelper.class);
        var errors = new ArrayList<Throwable>();
        var exits = new AtomicInteger();
        var launcher = new Launcher(preferences, helper, () -> {}, errors::add, exits::incrementAndGet);
        var completed = new AtomicReference<CompletionStage<Void>>();
        try (var logging = org.mockito.Mockito.mockStatic(com.github.k7t3.tcv.app.core.LoggerInitializer.class)) { launcher.init(); }

        robot.interact(() -> {
            var stage = new Stage();
            try {
                assertThatCode(() -> launcher.start(stage)).doesNotThrowAnyException();
                completed.set(launcher.requestShutdown());
            } finally {
                stage.close();
            }
        });

        completed.get().handle((ignored, failure) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst()).isInstanceOf(sqliteFailure ? RuntimeException.class : IOException.class);
        assertThat(exits.get()).isEqualTo(1);
        verify(helper, never()).getChannelGroupRepository();
    }
}
