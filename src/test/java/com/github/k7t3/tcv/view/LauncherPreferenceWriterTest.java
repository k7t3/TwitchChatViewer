package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(ApplicationExtension.class)
class LauncherPreferenceWriterTest {
    @TempDir Path directory;

    @Test
    void anOwnedDatabaseVersionWriteFailureStopsStartupBeforeModelConstruction(FxRobot robot) throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("user.data.file", directory.resolve("synthetic.db").toString());
        var failure = new IllegalStateException("synthetic version write failed");
        doThrow(failure).when(node).put("database.version", "V_1");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        var models = new AtomicInteger();
        var errors = new CopyOnWriteArrayList<Throwable>();
        var helper = new AppHelper(unexpected -> { throw new AssertionError(unexpected); });
        var launcher = new Launcher(preferences, helper, () -> {}, errors::add, () -> {}, owner -> {
            models.incrementAndGet();
            throw new AssertionError("model construction must not follow a startup write failure");
        }, (stage, model, owner, settings, bounds, hidden, failed) -> {
            throw new AssertionError("window construction must not follow a startup write failure");
        });
        var completed = new AtomicReference<CompletionStage<Void>>();
        try (var logging = mockStatic(com.github.k7t3.tcv.app.core.LoggerInitializer.class)) {
            launcher.init();
        }
        robot.interact(() -> {
            var stage = new Stage();
            try { launcher.start(stage); completed.set(launcher.requestShutdown()); }
            finally { stage.close(); }
        });
        completed.get().handle((ignored, error) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(models).hasValue(0);
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).hasCause(failure));
        assertThat(helper.getUserDataFile().getConnector().isConnected()).isFalse();
    }

    @Test
    void shutdownStopsPreferenceAdmissionBeforeReleasingTheKeyOwner(FxRobot robot) throws Exception {
        var node = new MemoryPreferences();
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        var keysClosed = new AtomicInteger();
        var launcher = new Launcher(preferences, mock(AppHelper.class), () -> {
            assertThat(node.get("database.version", null)).isEqualTo("V_1");
            assertThatThrownBy(() -> preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            keysClosed.incrementAndGet();
        }, failure -> {}, () -> {});
        preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);
        var completed = new AtomicReference<CompletionStage<Void>>();

        robot.interact(() -> completed.set(launcher.requestShutdown()));
        completed.get().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(keysClosed.get()).isEqualTo(1);
    }
}
