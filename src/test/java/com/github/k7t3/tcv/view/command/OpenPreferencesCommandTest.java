package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.view.MainViewFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.prefs.SettingsSaveService;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import com.github.k7t3.tcv.view.prefs.PreferencesPage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class OpenPreferencesCommandTest {
    @TempDir Path directory;

    @Test
    void headingTracksTheSelectedCategory(FxRobot robot) {
        var fixture = create(robot);
        try {
            robot.interact(() -> {
                fixture.command.execute();
                var scene = fixture.windows.getLast().getScene();
                var pages = (TreeView<?>) scene.lookup("#pagesTreeView");
                var heading = (javafx.scene.control.Label) scene.lookup("#pageHeading");
                assertThat(heading).as("active category heading").isNotNull();
                for (int i = 0; i < pages.getRoot().getChildren().size(); i++) {
                    var item = pages.getRoot().getChildren().get(i);
                    pages.getSelectionModel().select(i);
                    assertThat(heading.getText()).isEqualTo(((PreferencesPage<?>) item.getValue()).getName());
                }
            });
        } finally { fixture.close(robot); }
    }

    @Test
    void fontLookupFailureReportsToTheEditorThatStartedIt(FxRobot robot) {
        var fixture = create(robot);
        var failure = new IllegalStateException("font lookup unavailable");
        var lookup = new AtomicReference<FXTask<?>>();
        var errors = new AtomicReference<MockedStatic<ExceptionHandler>>();
        try {
            robot.interact(() -> {
                errors.set(mockStatic(ExceptionHandler.class));
                fixture.command.execute();
                try (var fonts = mockStatic(Font.class, CALLS_REAL_METHODS)) {
                    fonts.when(Font::getFamilies).thenThrow(failure);
                    lookup.set((FXTask<?>) fixture.queue.remove());
                    lookup.get().run();
                }
            });
            assertThatThrownBy(() -> lookup.get().get(5, TimeUnit.SECONDS)).hasCause(failure);

            robot.interact(() -> {
                var editor = fixture.windows.getLast();
                errors.get().verify(() -> ExceptionHandler.handle(editor, failure));
                errors.get().verifyNoMoreInteractions();
                assertThat(editor.isShowing()).isTrue();
            });
        } finally {
            try { fixture.close(robot); }
            finally { robot.interact(() -> { if (errors.get() != null) errors.get().close(); }); }
        }
    }

    @Test
    void aFailedSaveReportsToItsOpenEditorAndAllowsRetry(FxRobot robot) throws Exception {
        var fixture = create(robot);
        var failure = new IllegalStateException("settings store unavailable");
        var saves = new AtomicReference<MockedConstruction<SettingsSaveService>>();
        var errors = new AtomicReference<MockedStatic<ExceptionHandler>>();
        try {
            robot.interact(() -> {
                saves.set(mockConstruction(SettingsSaveService.class, (service, context) ->
                        when(service.save(any())).thenThrow(failure)));
                errors.set(mockStatic(ExceptionHandler.class));
                fixture.command.execute();
                ((Button) fixture.windows.getLast().getScene().lookup("#enterButton")).fire();
            });
            try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
                Runnable task;
                while ((task = fixture.queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }

            robot.interact(() -> {
                var editor = fixture.windows.getLast();
                verify(saves.get().constructed().getFirst()).save(any());
                errors.get().verify(() -> ExceptionHandler.handle(editor, failure));
                errors.get().verifyNoMoreInteractions();
                assertThat(editor.isShowing()).isTrue();
                assertThat(((Button) editor.getScene().lookup("#enterButton")).isDisabled()).isFalse();
            });
        } finally {
            try { fixture.close(robot); }
            finally {
                robot.interact(() -> {
                    if (errors.get() != null) errors.get().close();
                    if (saves.get() != null) saves.get().close();
                });
            }
        }
    }

    @ParameterizedTest(name = "close while creating {0}")
    @ValueSource(strings = {"stage", "controller", "page", "scene"})
    void closingDuringCreationCannotLeaveWorkOrOpenALateWindow(String boundary, FxRobot robot) {
        var fixture = create(robot);
        var viewCreated = new java.util.concurrent.atomic.AtomicBoolean();
        var retired = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            robot.interact(() -> {
                fixture.command.close();
                Runnable retire = () -> {
                    if (retired.compareAndSet(false, true)) fixture.command.close();
                };
                fixture.command = MainViewFactory.preferences(fixture.helper, fixture.preferences, editorStage -> {
                    viewCreated.set(true);
                    if (boundary.equals("controller")) retire.run();
                    return new PreferencesView(MainViewFactory.preferencesPages(fixture.helper, fixture.preferences, fixture.theme, editorStage));
                }, () -> {
                    var stage = fixture.newWindow();
                    if (boundary.equals("stage")) retire.run();
                    if (boundary.equals("scene")) stage.sceneProperty().addListener((observable, previous, scene) -> {
                        if (scene != null) retire.run();
                    });
                    return stage;
                });
                when(fixture.worker.getExecutor()).thenAnswer(invocation -> {
                    if (boundary.equals("page") && viewCreated.get()) retire.run();
                    return (java.util.concurrent.Executor) fixture.queue::add;
                });

                fixture.command.execute();

                assertThat(retired.get()).isTrue();
                assertThat(fixture.queue).as("closed editor must not leave a runnable font task")
                        .allSatisfy(task -> {
                            assertThat(task).isInstanceOf(java.util.concurrent.Future.class);
                            assertThat(((java.util.concurrent.Future<?>) task).isCancelled()).isTrue();
                        });
                assertThat(fixture.windows).hasSize(1);
                assertThat(fixture.windows.getFirst().isShowing()).isFalse();
                assertThat(fixture.windows.getFirst().getScene()).isNull();
                assertThat(fixture.command.isExecutable()).isFalse();
            });
        } finally {
            fixture.queue.forEach(task -> { if (task instanceof java.util.concurrent.Future<?> pending) pending.cancel(true); });
            fixture.close(robot);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void closingTheEditorDisconnectsLatePageSelection(FxRobot robot) {
        var fixture = create(robot);
        try {
            robot.interact(() -> {
                fixture.command.execute();
                var window = fixture.windows.getLast();
                var pages = (TreeView<PreferencesPage<?>>) window.getScene().lookup("#pagesTreeView");
                var content = (StackPane) window.getScene().lookup("#contentPane");
                var oldPage = pages.getRoot().getChildren().get(1);
                window.close();
                assertThat(content.getChildren()).isEmpty();
                var errors = new ArrayList<Throwable>();
                var thread = Thread.currentThread();
                var previous = thread.getUncaughtExceptionHandler();
                try {
                    thread.setUncaughtExceptionHandler((source, failure) -> errors.add(failure));

                    pages.getSelectionModel().select(oldPage);

                    assertThat(errors).as("closed page selection must not access cleared view state").isEmpty();
                    assertThat(content.getChildren()).isEmpty();
                    assertThat(window.getScene()).isNull();
                } finally {
                    thread.setUncaughtExceptionHandler(previous);
                }
            });
        } finally { fixture.close(robot); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellingByButtonOrWindowCloseRestoresTheEntirePreview(boolean button, FxRobot robot) throws Exception {
        var fixture = create(robot);
        try {
            robot.interact(() -> {
                fixture.command.execute();
                var window = fixture.windows.getLast();
                var choice = chooseTheme(window);
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.NORD);
                assertThat(fixture.theme.getThemeType()).isEqualTo(ThemeType.DARKER);
                if (button) ((Button) window.getScene().lookup("#cancelButton")).fire();
                else window.close();
                assertThat(window.isShowing()).isFalse();
                assertThat(window.getScene()).isNull();
                assertThat(fixture.command.isExecutable()).isTrue();
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.PRIMER);
                assertThat(fixture.theme.getThemeType()).isEqualTo(ThemeType.LIGHTER);
                choice.setValue(Theme.CUPERTINO);
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.PRIMER);
                assertThat(fixture.preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.PRIMER);
            });
        } finally { fixture.close(robot); }
    }

    @Test
    void acceptingWaitsForTheOwnedFlushAndKeepsTheSavedPreview(FxRobot robot) throws Exception {
        var fixture = create(robot);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                fixture.command.execute();
                var window = fixture.windows.getLast();
                chooseTheme(window);
                ((Button) window.getScene().lookup("#enterButton")).fire();
                assertThat(fixture.preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.PRIMER);
                assertThat(window.isShowing()).isTrue();
                assertThat(((Button) window.getScene().lookup("#enterButton")).isDisabled()).isTrue();
            });
            Runnable task;
            while ((task = fixture.queue.poll()) != null) {
                worker.submit(task).get(5, TimeUnit.SECONDS);
                robot.interact(() -> {});
            }
            robot.interact(() -> {
                assertThat(fixture.windows.getLast().isShowing()).isFalse();
                assertThat(fixture.preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
                assertThat(fixture.preferences.getGeneralPreferences().getThemeType()).isEqualTo(ThemeType.DARKER);
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.NORD);
                assertThat(fixture.theme.getThemeType()).isEqualTo(ThemeType.DARKER);
                fixture.command.close();
                assertThatThrownBy(fixture.command::execute).isInstanceOf(IllegalStateException.class);
            });
        } finally { fixture.close(robot); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closingDuringAnAcceptedSaveWaitsForApplicationBeforeAllowingAnotherEditor(boolean logout, FxRobot robot) throws Exception {
        var fixture = create(robot);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                var store = fixture.helper.getChatFilterStore();
                var initial = store.save(new com.github.k7t3.tcv.entity.service.ChatFilterEntityStore.Changes(
                        java.util.List.of(new com.github.k7t3.tcv.entity.ChatKeywordFilterEntity(0, 0, "existing")),
                        java.util.Set.of(), java.util.List.of(), java.util.Set.of()), () -> {});
                fixture.helper.getChatFilters().applySnapshot(com.github.k7t3.tcv.app.chat.filter.ChatFilters.Snapshot.from(initial));
                fixture.command.execute();
                var window = fixture.windows.getLast();
                chooseTheme(window);
                ((Button) window.getScene().lookup("#enterButton")).fire();
                window.close();
                assertThat(window.getScene()).isNull();
                assertThat(fixture.command.isExecutable()).isFalse();
                fixture.command.execute();
                assertThat(fixture.windows).hasSize(1);
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.PRIMER);
                if (logout) fixture.helper.getChatFilters().onLogout();
            });
            Runnable task;
            while ((task = fixture.queue.poll()) != null) {
                worker.submit(task).get(5, TimeUnit.SECONDS);
                robot.interact(() -> {});
            }
            robot.interact(() -> {
                assertThat(fixture.command.isExecutable()).isTrue();
                assertThat(fixture.preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
                assertThat(fixture.theme.getTheme()).isEqualTo(Theme.NORD);
                if (logout) assertThat(fixture.helper.getChatFilters().getKeywordEntries()).isEmpty();
                else assertThat(fixture.helper.getChatFilters().getKeywordEntries()).hasSize(1);
                fixture.command.execute();
                var choice = (ChoiceBox<?>) fixture.windows.getLast().getScene().lookup("#themeChoiceBox");
                assertThat(choice.getValue()).isEqualTo(Theme.NORD);
            });
        } finally { fixture.close(robot); }
    }

    @Test
    void aPartialFxmlFailureReleasesTheWindowAndAllowsOpeningAgain(FxRobot robot) throws Exception {
        var fixture = create(robot);
        try {
            robot.interact(() -> {
                fixture.command.close();
                var failure = new IllegalStateException("preferences FXML failed");
                var calls = new AtomicInteger();
                java.util.function.Function<Stage, PreferencesView> factory = editorStage -> calls.getAndIncrement() == 0
                        ? new PreferencesView(MainViewFactory.preferencesPages(fixture.helper, fixture.preferences, fixture.theme, editorStage)) {
                            @Override public void initialize(java.net.URL location, java.util.ResourceBundle resources) { throw failure; }
                        } : new PreferencesView(MainViewFactory.preferencesPages(fixture.helper, fixture.preferences, fixture.theme, editorStage));
                fixture.command = MainViewFactory.preferences(fixture.helper, fixture.preferences, factory, fixture::newWindow);
                assertThatThrownBy(fixture.command::execute).hasStackTraceContaining("preferences FXML failed");
                assertThat(fixture.windows.getLast().isShowing()).isFalse();
                assertThat(fixture.windows.getLast().getScene()).isNull();
                assertThat(fixture.command.isExecutable()).isTrue();
                fixture.command.execute();
                assertThat(fixture.windows.getLast().isShowing()).isTrue();
                fixture.command.close();
                assertThat(fixture.windows.getLast().getScene()).isNull();
                assertThat(fixture.helper.getChatFilters()).isNotNull();
            });
        } finally { fixture.close(robot); }
    }

    @SuppressWarnings("unchecked")
    private static ChoiceBox<Theme> chooseTheme(Stage window) {
        var choice = (ChoiceBox<Theme>) window.getScene().lookup("#themeChoiceBox");
        choice.setValue(Theme.NORD);
        ((ChoiceBox<ThemeType>) window.getScene().lookup("#themeTypeChoiceBox")).setValue(ThemeType.DARKER);
        return choice;
    }

    private Fixture create(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        robot.interact(() -> reference.set(new Fixture()));
        return reference.get();
    }

    private final class Fixture {
        final String previousStyle = Application.getUserAgentStylesheet();
        final TaskWorker worker = mock(TaskWorker.class);
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final AppHelper helper;
        final AppPreferences preferences = new AppPreferences(new MemoryPreferences());
        final ThemeManager theme = new ThemeManager();
        final Stage owner = new Stage();
        final ArrayList<Stage> windows = new ArrayList<>();
        OpenPreferencesCommand command;
        Fixture() {
            when(worker.getExecutor()).thenReturn(queue::add);
            helper = new AppHelper(worker, new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
            var data = new UserDataFile(directory.resolve("settings.db"));
            try { data.connectDatabase(DatabaseVersion.EMPTY); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            helper.setUserDataFile(data);
            helper.setPrimaryStage(owner);
            owner.setScene(new Scene(new StackPane(), 420, 240));
            theme.setScene(owner.getScene());
            preferences.getGeneralPreferences().setTheme(Theme.PRIMER);
            preferences.getGeneralPreferences().setThemeType(ThemeType.LIGHTER);
            theme.setTheme(Theme.PRIMER);
            theme.setThemeType(ThemeType.LIGHTER);
            owner.show();
            command = MainViewFactory.preferences(helper, preferences,
                    editorStage -> new PreferencesView(MainViewFactory.preferencesPages(helper, preferences, theme, editorStage)), this::newWindow);
        }
        Stage newWindow() { var stage = new Stage(); windows.add(stage); return stage; }
        void close(FxRobot robot) {
            robot.interact(() -> {
                command.close();
                windows.forEach(Stage::close);
                owner.close();
                helper.prepareShutdown();
                theme.close();
                Application.setUserAgentStylesheet(previousStyle);
            });
            Runnable task;
            while ((task = queue.poll()) != null) task.run();
            helper.close();
        }
    }
}
