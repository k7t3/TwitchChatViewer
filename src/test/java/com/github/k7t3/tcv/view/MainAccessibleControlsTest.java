package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainAccessibleControlsTest {
    @TempDir Path directory;

    @Test
    void theSearchGraphicButtonExposesItsPurposeToAccessibility(FxRobot robot) {
        var fixture = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var current = new Fixture(); fixture.set(current);
                current.window.show();
                var search = (Button) current.stage.getScene().lookup("#searchChannelButton");

                assertThat(search.queryAccessibleAttribute(AccessibleAttribute.TEXT))
                        .isEqualTo(Resources.getString("command.open.search"));
            });
        } finally { close(fixture.get(), robot); }
    }

    @Test
    void theUserMenuExposesItsPurposeToAccessibility(FxRobot robot) {
        var fixture = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var current = new Fixture(); fixture.set(current);
                current.window.show();
                var menu = (MenuButton) current.stage.getScene().lookup("#userMenuButton");
                var expected = Resources.getResourceBundle().getLocale().getLanguage().equals("ja")
                        ? "ユーザーメニュー" : "User menu";

                assertThat(menu.queryAccessibleAttribute(AccessibleAttribute.TEXT)).isEqualTo(expected);
            });
        } finally { close(fixture.get(), robot); }
    }

    @Test
    void theChannelOptionsMenuExposesItsPurposeToAccessibility(FxRobot robot) {
        var fixture = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var current = new Fixture(); fixture.set(current);
                current.window.show();
                var menu = (MenuButton) current.stage.getScene().lookup("#optionMenuButton");
                var expected = Resources.getResourceBundle().getLocale().getLanguage().equals("ja")
                        ? "チャンネル表示オプション" : "Channel display options";

                assertThat(menu.queryAccessibleAttribute(AccessibleAttribute.TEXT)).isEqualTo(expected);
            });
        } finally { close(fixture.get(), robot); }
    }

    private void close(Fixture fixture, FxRobot robot) {
        if (fixture == null) return;
        CloseActions.runAll(() -> robot.interact(() -> CloseActions.runAll(fixture.window::close,
                fixture.model::close, fixture.helper::prepareShutdown,
                () -> Application.setUserAgentStylesheet(fixture.previousStyle))), () -> {
            Runnable pending;
            while ((pending = fixture.queue.poll()) != null) pending.run();
            fixture.helper.close();
        });
    }

    private final class Fixture {
        final String previousStyle = Application.getUserAgentStylesheet();
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final Stage stage = new Stage();
        final ArrayList<Throwable> failures = new ArrayList<>();
        final AppHelper helper;
        final MainViewModel model;
        final MainWindow window;

        Fixture() {
            var worker = mock(TaskWorker.class);
            when(worker.getExecutor()).thenReturn(queue::add);
            helper = new AppHelper(worker, new EventPublishers(), failures::add);
            var file = new UserDataFile(directory.resolve("userdata.db"));
            try { file.connectDatabase(DatabaseVersion.EMPTY); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            helper.setUserDataFile(file); helper.setPrimaryStage(stage);
            var preferences = new AppPreferences(new MemoryPreferences());
            var theme = new ThemeManager();
            model = MainModelFactory.create(helper, helper.getChannelGroupRepository(), helper.getImageLoader(), failures::add);
            var controller = spy(MainViewFactory.create(stage, model, helper, preferences, theme, mock(CredentialStore.class)));
            doNothing().when(controller).startMainView();
            window = new MainWindow(stage, model, helper, preferences, helper.getWindowBoundsService().getBounds("main"),
                    () -> {}, failures::add, controller, theme);
        }
    }
}
