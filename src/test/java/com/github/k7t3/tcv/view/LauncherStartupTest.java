package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.LoggerInitializer;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.core.WindowBounds;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class LauncherStartupTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aSettingsRecoveryFailureReleasesTheDatabaseWithoutConstructingViews(boolean error, FxRobot robot) throws Exception {
        var fixture = new Fixture();
        Throwable failure = error ? new AssertionError("settings recovery failed") : new IllegalStateException("settings recovery failed");
        doAnswer(invocation -> { throw failure; }).when(fixture.helper).recoverSettings(fixture.preferences);
        var launcher = fixture.launcher();
        fixture.initialize(launcher);
        var completed = new AtomicReference<CompletionStage<Void>>();

        robot.interact(() -> { launcher.start(new Stage()); completed.set(launcher.requestShutdown()); });

        assertThatThrownBy(() -> completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(fixture.errors).containsExactly(failure);
        verify(fixture.bounds, never()).getBounds(anyString());
        verify(fixture.window, never()).show();
        verify(fixture.helper).close();
        assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
    }

    @Test
    void settingsRecoveryRunsOffFxBeforeBoundsAndViewModelConstruction(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var recovered = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            assertThat(fixture.data.get().getConnector().isConnected()).isTrue();
            recovered.set(true);
            return null;
        }).when(fixture.helper).recoverSettings(fixture.preferences);
        when(fixture.bounds.getBounds("main")).thenAnswer(invocation -> {
            assertThat(recovered).isTrue();
            return fixture.savedBounds;
        });
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> {}, fixture.errors::add, () -> {},
                helper -> {
                    assertThat(recovered).isTrue();
                    return fixture.model;
                }, (stage, model, helper, preferences, bounds, hidden, failed) -> fixture.window);
        fixture.initialize(launcher);
        try {
            assertThat(recovered).isTrue();
            robot.interact(() -> launcher.start(new Stage()));
            verify(fixture.helper).recoverSettings(fixture.preferences);
            verify(fixture.window).show();
            assertThat(fixture.errors).isEmpty();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void aModelReturnedAfterShutdownIsReleasedWithoutConstructingAWindow(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var owner = new AtomicReference<Launcher>();
        var completion = new AtomicReference<CompletionStage<Void>>();
        var windows = new AtomicInteger();
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> {}, fixture.errors::add, () -> {},
                helper -> {
                    completion.set(owner.get().requestShutdown());
                    return fixture.model;
                }, (stage, model, helper, preferences, bounds, hidden, failed) -> {
                    windows.incrementAndGet();
                    return fixture.window;
                });
        owner.set(launcher);
        fixture.initialize(launcher);
        try {
            robot.interact(() -> launcher.start(new Stage()));
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(fixture.model).close();
            assertThat(windows).hasValue(0);
            verify(fixture.window, never()).show();
            verify(fixture.helper).close();
            assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
            assertThat(fixture.errors).isEmpty();
        } finally { robot.interact(launcher::stop); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownWaitsForTheModelFactoryAndItsLateResultBeforeReleasingSharedResources(boolean forced, FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var owner = new AtomicReference<Launcher>();
        var completion = new AtomicReference<CompletionStage<Void>>();
        var releasing = new CountDownLatch(1);
        var releasedDuringConstruction = new java.util.concurrent.atomic.AtomicBoolean();
        var order = new CopyOnWriteArrayList<String>();
        doAnswer(invocation -> { order.add("model"); return null; }).when(fixture.model).close();
        doAnswer(invocation -> {
            releasing.countDown();
            order.add("helper");
            fixture.data.get().closeDatabase();
            return null;
        }).when(fixture.helper).close();
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> order.add("keys"),
                fixture.errors::add, () -> {}, helper -> {
                    if (forced) owner.get().stop();
                    completion.set(owner.get().requestShutdown());
                    try { releasedDuringConstruction.set(releasing.await(100, TimeUnit.MILLISECONDS)); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                    return fixture.model;
                }, (stage, model, helper, preferences, bounds, hidden, failed) -> fixture.window);
        owner.set(launcher);
        fixture.initialize(launcher);
        try {
            robot.interact(() -> launcher.start(new Stage()));
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThat(releasedDuringConstruction).isFalse();
            assertThat(order).containsExactly("model", "helper", "keys");
            verify(fixture.window, never()).show();
            assertThat(fixture.errors).isEmpty();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void aWindowReturnedAfterShutdownIsClosedWithoutBeingShown(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var owner = new AtomicReference<Launcher>();
        var completion = new AtomicReference<CompletionStage<Void>>();
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> {}, fixture.errors::add, () -> {},
                helper -> fixture.model, (stage, model, helper, preferences, bounds, hidden, failed) -> {
                    completion.set(owner.get().requestShutdown());
                    return fixture.window;
                });
        owner.set(launcher);
        fixture.initialize(launcher);
        try {
            robot.interact(() -> launcher.start(new Stage()));
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

            verify(fixture.window).close();
            verify(fixture.window, never()).show();
            verify(fixture.model).close();
            verify(fixture.helper).close();
            assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
            assertThat(fixture.errors).isEmpty();
        } finally { robot.interact(launcher::stop); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownDuringPreferenceBindingStopsFurtherStartup(boolean chat, FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var owner = new AtomicReference<Launcher>();
        var completion = new AtomicReference<CompletionStage<Void>>();
        var windows = new AtomicInteger();
        if (chat) {
            var chats = fixture.model.getChatContainer();
            doAnswer(invocation -> { completion.set(owner.get().requestShutdown()); return null; })
                    .when(chats).bindChatPreferences(any());
        } else {
            var channels = fixture.model.getChannelListViewModel();
            doAnswer(invocation -> { completion.set(owner.get().requestShutdown()); return null; })
                    .when(channels).bindGeneralPreferences(any());
        }
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> {}, fixture.errors::add, () -> {},
                helper -> fixture.model, (stage, model, helper, preferences, bounds, hidden, failed) -> {
                    windows.incrementAndGet();
                    return fixture.window;
                });
        owner.set(launcher);
        fixture.initialize(launcher);
        try {
            robot.interact(() -> launcher.start(new Stage()));
            completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThat(windows).hasValue(0);
            verify(fixture.model).close();
            verify(fixture.window, never()).show();
            assertThat(fixture.errors).isEmpty();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void aLateWindowReleaseFailureIsPreservedAfterAnEarlierModelReleaseFailure(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var owner = new AtomicReference<Launcher>();
        var completion = new AtomicReference<CompletionStage<Void>>();
        var primary = new IllegalStateException("model cleanup failed");
        var late = new IllegalArgumentException("late window cleanup failed");
        var keysClosed = new AtomicInteger();
        doThrow(primary).when(fixture.model).close();
        doThrow(late).when(fixture.window).close();
        var launcher = new Launcher(fixture.preferences, fixture.helper, keysClosed::incrementAndGet,
                fixture.errors::add, () -> {}, helper -> fixture.model,
                (stage, model, helper, preferences, bounds, hidden, failed) -> {
                    completion.set(owner.get().requestShutdown());
                    return fixture.window;
                });
        owner.set(launcher);
        fixture.initialize(launcher);
        try {
            robot.interact(() -> launcher.start(new Stage()));
            assertThatThrownBy(() -> completion.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(primary);

            assertThat(primary.getSuppressed()).containsExactly(late);
            verify(fixture.model).close();
            verify(fixture.window).close();
            verify(fixture.window, never()).show();
            verify(fixture.helper).close();
            assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
            assertThat(keysClosed).hasValue(1);
            verify(fixture.logging).close();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void startupLoadsAndReleasesTheDatabaseThroughTheRealApplicationServices(FxRobot robot) throws Exception {
        var path = directory.resolve("startup.db");
        var stored = new WindowBoundsEntity("main", 30, 40, 910, 620, false);
        var seed = new UserDataFile(path.toString());
        try {
            seed.connectDatabase(DatabaseVersion.EMPTY);
            new WindowBoundsEntityService(seed.getConnector()).save(stored);
        } finally { seed.closeDatabase(); }
        var preferences = spy(new AppPreferences(new MemoryPreferences()));
        preferences.getGeneralPreferences().setUserDataFilePath(path.toString());
        preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.latest());
        var helper = new AppHelper(new TaskWorker(), new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var model = mock(MainViewModel.class);
        when(model.getChannelListViewModel()).thenReturn(mock(TwitchChannelListViewModel.class));
        when(model.getChatContainer()).thenReturn(mock(ChatRoomContainerViewModel.class));
        var window = mock(ApplicationWindow.class);
        var logging = mock(LoggerInitializer.LoggingSession.class);
        var errors = new CopyOnWriteArrayList<Throwable>();
        var exits = new AtomicInteger();
        var launcher = new Launcher(preferences, helper, () -> {}, errors::add, exits::incrementAndGet,
                owner -> model, (stage, main, owner, prefs, bounds, hidden, failed) -> {
                    assertThat(Platform.isFxApplicationThread()).isTrue();
                    assertThat(bounds).isEqualTo(new WindowBounds(30, 40, 910, 620, false));
                    return window;
                });
        var complete = new AtomicReference<CompletionStage<Void>>();
        try {
            try (var initializer = mockStatic(LoggerInitializer.class)) {
                initializer.when(LoggerInitializer::initialize).thenReturn(logging);
                launcher.init();
            }
            assertThat(helper.getUserDataFile().getConnector().isConnected()).isTrue();
            doAnswer(invocation -> {
                assertThat(Platform.isFxApplicationThread()).isFalse();
                assertThat(helper.getUserDataFile().getConnector().isConnected()).isFalse();
                return invocation.callRealMethod();
            }).when(preferences).save();
            robot.interact(() -> { launcher.start(new Stage()); complete.set(launcher.requestShutdown()); });
            complete.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(errors).isEmpty();
            assertThat(exits).hasValue(1);
            verify(window).show();
            verify(preferences).save();
            verify(logging).close();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void startupFailureClosesLoggingLastEvenWhenOtherResourcesCannotClose(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var order = new CopyOnWriteArrayList<String>();
        var failure = new IllegalStateException("schema version write failed");
        var keyFailure = new IllegalStateException("keys could not close");
        var logFailure = new IllegalStateException("logging could not close");
        var logger = java.util.logging.Logger.getLogger(Launcher.class.getName());
        var diagnostic = mock(java.util.logging.Handler.class);
        doAnswer(invocation -> {
            java.util.logging.LogRecord record = invocation.getArgument(0);
            if (record.getMessage().equals("application resource release failed")) {
                assertThat(record.getThrown()).isSameAs(failure);
                assertThat(failure.getSuppressed()).containsExactly(keyFailure);
                order.add("diagnostic");
            }
            return null;
        }).when(diagnostic).publish(any());
        doThrow(failure).when(fixture.general).setDatabaseVersion(any());
        doAnswer(invocation -> {
            fixture.data.get().closeDatabase();
            order.add("database");
            return null;
        }).when(fixture.helper).close();
        doAnswer(invocation -> { order.add("preferences"); return null; }).when(fixture.preferences).close();
        doAnswer(invocation -> { order.add("logging"); throw logFailure; }).when(fixture.logging).close();
        var launcher = new Launcher(fixture.preferences, fixture.helper,
                () -> { order.add("keys"); throw keyFailure; },
                error -> { assertThat(error).isSameAs(failure); order.add("report"); },
                () -> order.add("exit"));
        logger.addHandler(diagnostic);
        try {
            fixture.initialize(launcher);
            var complete = new AtomicReference<CompletionStage<Void>>();
            robot.interact(() -> { launcher.start(new Stage()); complete.set(launcher.requestShutdown()); });
            assertThatThrownBy(() -> complete.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(order).containsExactly("database", "preferences", "keys", "diagnostic", "logging", "report", "exit");
            assertThat(failure.getSuppressed()).containsExactly(keyFailure, logFailure);
            verify(fixture.logging).close();
        } finally { logger.removeHandler(diagnostic); }
    }

    @Test
    void forcedStopAlsoClosesTheInitializedLoggingSessionOnce(FxRobot robot) {
        var fixture = new Fixture();
        var launcher = fixture.launcher();
        fixture.initialize(launcher);
        robot.interact(() -> { launcher.stop(); launcher.stop(); });
        verify(fixture.logging).close();
        assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
    }

    @Test
    void anErrorDialogCannotHoldTheDatabaseAndKeyServiceOpen(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var failure = new IllegalStateException("cannot record schema version");
        doThrow(failure).when(fixture.general).setDatabaseVersion(any());
        var reporting = new CountDownLatch(1);
        var dismiss = new CountDownLatch(1);
        var keysClosed = new AtomicInteger();
        var launcher = new Launcher(fixture.preferences, fixture.helper, keysClosed::incrementAndGet,
                error -> {
                    reporting.countDown();
                    try {
                        if (!dismiss.await(5, TimeUnit.SECONDS)) throw new AssertionError("error report was not dismissed");
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                }, () -> {}, helper -> fixture.model,
                (stage, model, helper, preferences, savedBounds, hidden, failed) -> fixture.window);
        fixture.initialize(launcher);
        var complete = new AtomicReference<CompletionStage<Void>>();
        Platform.runLater(() -> { launcher.start(new Stage()); complete.set(launcher.requestShutdown()); });
        try {
            assertThat(reporting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
            assertThat(keysClosed).hasValue(1);
            verify(fixture.helper).close();
        } finally {
            dismiss.countDown();
            robot.interact(() -> {});
            if (complete.get() != null) complete.get().handle((value, error) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void databaseAndBoundsAreLoadedBeforeTheFxViewIsConstructed(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var loaded = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            assertThat(fixture.data.get().getConnector().isConnected()).isTrue();
            loaded.incrementAndGet();
            return null;
        }).when(fixture.general).setDatabaseVersion(any());
        when(fixture.bounds.getBounds("main")).thenAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            loaded.incrementAndGet();
            return fixture.savedBounds;
        });
        doAnswer(invocation -> { assertThat(Platform.isFxApplicationThread()).isTrue(); return null; }).when(fixture.window).show();
        var launcher = fixture.launcher();
        fixture.initialize(launcher);
        var complete = new AtomicReference<CompletionStage<Void>>();
        try {
            assertThat(loaded).hasValue(2);
            verify(fixture.window, never()).show();
            robot.interact(() -> { launcher.start(new Stage()); complete.set(launcher.requestShutdown()); });
            complete.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void shutdownDuringInitializationWaitsForTheOwnedConnectionWithoutBlockingFx(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(fixture.general.getDatabaseVersion()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("database initialization was not released");
            return DatabaseVersion.EMPTY;
        });
        var launcher = fixture.launcher();
        var completed = new AtomicReference<CompletionStage<Void>>();
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var initializing = workers.submit(() -> fixture.initialize(launcher));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> completed.set(launcher.requestShutdown()));
                robot.interact(() -> assertThat(completed.get().toCompletableFuture().isDone()).isFalse());
                verify(fixture.helper, never()).close();
                release.countDown();
                initializing.get(5, TimeUnit.SECONDS);
                completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(fixture.data.get().getConnector().isConnected()).isFalse();
                verify(fixture.window, never()).show();
                verify(fixture.helper).close();
            } finally { release.countDown(); }
        } finally {
            if (completed.get() != null) completed.get().handle((value, failure) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void startWithoutInitializationCannotPerformIoOnFx(FxRobot robot) {
        var fixture = new Fixture();
        var launcher = fixture.launcher();
        try {
            robot.interact(() -> assertThatThrownBy(() -> launcher.start(new Stage())).isInstanceOf(IllegalStateException.class));
            verify(fixture.general, never()).getUserDataFilePath();
        } finally { robot.interact(launcher::stop); }
    }

    @Test
    void initFailureIsReportedOnFxAndDoesNotOpenTheDatabase(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var failure = new IllegalStateException("logging initialization failed");
        var launcher = fixture.launcher();
        try (var logging = mockStatic(LoggerInitializer.class)) {
            logging.when(LoggerInitializer::initialize).thenThrow(failure);
            launcher.init();
        }
        var completed = new AtomicReference<CompletionStage<Void>>();
        robot.interact(() -> { launcher.start(new Stage()); completed.set(launcher.requestShutdown()); });
        assertThatThrownBy(() -> completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(fixture.errors).containsExactly(failure);
        verify(fixture.general, never()).getUserDataFilePath();
        verify(fixture.helper).close();
    }

    @Test
    void preferencesAreFlushedInTheBackgroundAfterAcceptedWritesFinish(FxRobot robot) throws Exception {
        var node = new MemoryPreferences();
        var preferences = spy(new AppPreferences(node));
        var worker = spy(new TaskWorker());
        var helper = new AppHelper(worker, new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var release = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            assertThat(node.getInt("late-value", 0)).isEqualTo(123);
            return invocation.callRealMethod();
        }).when(preferences).save();
        worker.getExecutor().execute(() -> {
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("accepted write was not released");
                node.putInt("late-value", 123);
            } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        });
        var launcher = new Launcher(preferences, helper, () -> {}, error -> {}, () -> {});
        var completed = new AtomicReference<CompletionStage<Void>>();
        try {
            robot.interact(() -> completed.set(launcher.requestShutdown()));
            assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
            verify(preferences, never()).save();
            release.countDown();
            completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(preferences).save();
        } finally {
            release.countDown();
            if (completed.get() != null) completed.get().handle((ignored, failure) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);
            helper.close();
        }
    }

    @Test
    void aVersionWriteFailureStillClosesTheConnectedDatabaseAndAllOtherResources(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var connected = new AtomicReference<UserDataFile>();
        doAnswer(invocation -> { connected.set(invocation.getArgument(0)); return null; }).when(fixture.helper).setUserDataFile(any());
        doAnswer(invocation -> { connected.get().closeDatabase(); return null; }).when(fixture.helper).close();
        var failure = new IllegalStateException("cannot record schema version");
        doThrow(failure).when(fixture.general).setDatabaseVersion(any());
        var launcher = fixture.launcher();
        var completed = new AtomicReference<CompletionStage<Void>>();
        fixture.initialize(launcher);
        robot.interact(() -> { launcher.start(new Stage()); completed.set(launcher.requestShutdown()); });
        assertThatThrownBy(() -> completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCause(failure);
        assertThat(connected.get()).isNotNull();
        assertThat(connected.get().getConnector().isConnected()).isFalse();
        assertThat(fixture.errors).containsExactly(failure);
        verify(fixture.helper).close();
        verify(fixture.window, never()).show();
        robot.interact(launcher::stop);
        verify(fixture.helper).close();
    }

    @Test
    void aPartialWindowFailureClosesTheWindowAndModelBeforeBackgroundRelease(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var failure = new IllegalStateException("FXML failed");
        var events = new CopyOnWriteArrayList<String>();
        doThrow(failure).when(fixture.window).show();
        doAnswer(invocation -> { assertThat(Platform.isFxApplicationThread()).isTrue(); events.add("window"); return null; }).when(fixture.window).close();
        doAnswer(invocation -> { events.add("model"); return null; }).when(fixture.model).close();
        doAnswer(invocation -> { assertThat(Platform.isFxApplicationThread()).isFalse(); events.add("helper"); if (fixture.data.get() != null) fixture.data.get().closeDatabase(); return null; }).when(fixture.helper).close();
        var launcher = fixture.launcher();
        var completed = new AtomicReference<CompletionStage<Void>>();
        fixture.initialize(launcher);
        robot.interact(() -> { launcher.start(new Stage()); completed.set(launcher.requestShutdown()); });
        assertThatThrownBy(() -> completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(events).containsExactly("window", "model", "helper");
        assertThat(fixture.errors).containsExactly(failure);
        verify(fixture.preferences).close();
    }

    @Test
    void evenAnErrorReporterFailureCannotSkipCleanupOrTheExitNotification(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var primary = new IllegalStateException("theme failed");
        var reporting = new IllegalStateException("reporter failed");
        var cleanup = new IllegalStateException("window cleanup failed");
        var events = new CopyOnWriteArrayList<String>();
        doThrow(primary).when(fixture.window).show();
        doThrow(cleanup).when(fixture.window).close();
        var launcher = new Launcher(fixture.preferences, fixture.helper, () -> events.add("keys"),
                error -> { throw reporting; }, () -> events.add("exit"), helper -> fixture.model,
                (stage, model, helper, preferences, savedBounds, hidden, failed) -> fixture.window);
        var completed = new AtomicReference<CompletionStage<Void>>();
        fixture.initialize(launcher);
        robot.interact(() -> { assertThatCode(() -> launcher.start(new Stage())).doesNotThrowAnyException(); completed.set(launcher.requestShutdown()); });
        assertThatThrownBy(() -> completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(primary);
        assertThat(primary.getSuppressed()).containsExactly(cleanup, reporting);
        assertThat(events).containsExactly("keys", "exit");
        verify(fixture.model).close();
        verify(fixture.helper).close();
    }

    @Test
    void forcedStopIsAlsoOwnedAndDoesNotRepeatPreparationOrRelease(FxRobot robot) {
        var fixture = new Fixture();
        var keys = new AtomicInteger();
        var launcher = new Launcher(fixture.preferences, fixture.helper, keys::incrementAndGet, error -> {}, () -> {});
        robot.interact(() -> { launcher.stop(); launcher.stop(); });
        verify(fixture.preferences).close();
        verify(fixture.helper).close();
        assertThat(keys).hasValue(1);
    }

    private final class Fixture {
        final AppPreferences preferences = mock(AppPreferences.class);
        final GeneralPreferences general = mock(GeneralPreferences.class);
        final AppHelper helper = mock(AppHelper.class);
        final WindowBoundsService bounds = mock(WindowBoundsService.class);
        final WindowBounds savedBounds = new WindowBounds(0, 0, 1024, 768, false);
        final MainViewModel model = mock(MainViewModel.class);
        final ApplicationWindow window = mock(ApplicationWindow.class);
        final LoggerInitializer.LoggingSession logging = mock(LoggerInitializer.LoggingSession.class);
        final ArrayList<Throwable> errors = new ArrayList<>();
        final AtomicReference<UserDataFile> data = new AtomicReference<>();

        Fixture() {
            when(preferences.getGeneralPreferences()).thenReturn(general);
            when(helper.getWindowBoundsService()).thenReturn(bounds);
            when(bounds.getBounds("main")).thenReturn(savedBounds);
            when(model.getChannelListViewModel()).thenReturn(mock(TwitchChannelListViewModel.class));
            when(model.getChatContainer()).thenReturn(mock(ChatRoomContainerViewModel.class));
            when(general.getUserDataFilePath()).thenReturn(directory.resolve("userdata.db").toString());
            when(general.getDatabaseVersion()).thenReturn(DatabaseVersion.EMPTY);
            // 実 DB の所有はテストが最後まで回収する。接続を捕捉するテストでは上書きする。
            doAnswer(invocation -> { data.set(invocation.getArgument(0)); return null; }).when(helper).setUserDataFile(any());
            doAnswer(invocation -> { if (data.get() != null) data.get().closeDatabase(); return null; }).when(helper).close();
        }
        void initialize(Launcher launcher) {
            try (var initializer = mockStatic(LoggerInitializer.class)) {
                initializer.when(LoggerInitializer::initialize).thenReturn(logging);
                launcher.init();
            }
        }
        Launcher launcher() {
            return new Launcher(preferences, helper, () -> {}, errors::add, () -> {}, helper -> model,
                    (stage, model, helper, preferences, savedBounds, hidden, failed) -> window);
        }
    }
}
