package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.view.MainViewFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.view.help.AboutView;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayList;
import java.awt.Desktop;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class OpenAboutCommandTest {
    @Test
    void theProjectLinkExposesItsPurposeToAccessibility(FxRobot robot) {
        robot.interact(() -> {
            var helper = mock(AppHelper.class);
            when(helper.getExecutor()).thenReturn(task -> {});
            var owner = new Stage();
            owner.setScene(new Scene(new Pane()));
            owner.show();
            when(helper.getPrimaryStage()).thenReturn(owner);
            var stages = new ArrayList<Stage>();
            var command = MainViewFactory.about(helper, AboutView::new, () -> {
                var stage = new Stage(); stages.add(stage); return stage;
            });
            try {
                command.execute();
                var button = (Button) stages.getFirst().getScene().lookup("#gitHubButton");

                assertThat(button.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                        .isEqualTo(com.github.k7t3.tcv.app.core.Resources.getString("browse.github"));
            } finally {
                try { command.close(); }
                finally { stages.forEach(Stage::hide); owner.close(); }
            }
        });
    }

    @Test
    void aFailedLinkReportsToTheAboutWindowThatStartedIt(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var owner = new AtomicReference<Stage>();
        var stages = new ArrayList<Stage>();
        var command = new AtomicReference<OpenAboutCommand>();
        var errors = new AtomicReference<MockedStatic<ExceptionHandler>>();
        var notified = new CountDownLatch(1);
        var failure = new UnsupportedOperationException("browser unavailable");
        try {
            robot.interact(() -> {
                var helper = mock(AppHelper.class);
                when(helper.getExecutor()).thenReturn(queue::add);
                owner.set(new Stage());
                owner.get().setScene(new Scene(new Pane()));
                owner.get().show();
                when(helper.getPrimaryStage()).thenReturn(owner.get());
                command.set(MainViewFactory.about(helper, AboutView::new, () -> {
                    var stage = new Stage(); stages.add(stage); return stage;
                }));
                var handler = mockStatic(ExceptionHandler.class);
                errors.set(handler);
                handler.when(() -> ExceptionHandler.handle(any(Stage.class), any(Throwable.class))).thenAnswer(call -> {
                    notified.countDown(); return null;
                });
                command.get().execute();

                ((Button) stages.getFirst().getScene().lookup("#gitHubButton")).fire();

                assertThat(queue).hasSize(1);
                try (var desktop = mockStatic(Desktop.class)) {
                    desktop.when(Desktop::getDesktop).thenThrow(failure);
                    queue.remove().run();
                    desktop.verify(Desktop::getDesktop);
                }
            });
            assertThat(notified.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                errors.get().verify(() -> ExceptionHandler.handle(stages.getFirst(), failure));
                errors.get().verifyNoMoreInteractions();
                assertThat(stages.getFirst().isShowing()).isTrue();
                assertThat(owner.get().isShowing()).isTrue();
            });
        } finally {
            robot.interact(() -> {
                try {
                    if (command.get() != null) command.get().close();
                    stages.forEach(Stage::hide);
                    if (owner.get() != null) owner.get().close();
                } finally { if (errors.get() != null) errors.get().close(); }
            });
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hidingOrClosingTheCommandReleasesItsWindowAndController(boolean commandClose, FxRobot robot) {
        robot.interact(() -> {
            var helper = mock(AppHelper.class); when(helper.getExecutor()).thenReturn(task -> {});
            var owner = new Stage(); owner.setScene(new Scene(new Pane())); owner.show();
            when(helper.getPrimaryStage()).thenReturn(owner);
            var stages = new ArrayList<Stage>(); var views = new ArrayList<AboutView>();
            var command = MainViewFactory.about(helper, () -> { var view = spy(new AboutView()); views.add(view); return view; }, () -> {
                var stage = new Stage(); stages.add(stage);
                return stage;
            });
            try {
                command.execute(); var child = stages.getFirst();
                assertThat(child.isShowing()).isTrue(); assertThat(child.getOwner()).isSameAs(owner);
                assertThat(child.getModality()).isEqualTo(Modality.WINDOW_MODAL);
                if (commandClose) command.close(); else child.close();
                assertThat(child.isShowing()).isFalse(); assertThat(child.getScene()).isNull();
                verify(views.getFirst()).close();
                assertThat(command.isExecutable()).isEqualTo(!commandClose);
            } finally { command.close(); stages.forEach(Stage::hide); owner.hide(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closingDuringAFactoryReleasesEvenTheResourceReturnedAfterClose(boolean duringStage, FxRobot robot) {
        robot.interact(() -> {
            var helper = mock(AppHelper.class); when(helper.getExecutor()).thenReturn(task -> {});
            var reference = new java.util.concurrent.atomic.AtomicReference<OpenAboutCommand>();
            var view = spy(new AboutView()); var stages = new ArrayList<Stage>();
            var command = MainViewFactory.about(helper, () -> {
                if (!duringStage) reference.get().close();
                return view;
            }, () -> {
                var stage = new Stage(); stages.add(stage);
                if (duringStage) reference.get().close();
                return stage;
            });
            reference.set(command);
            try {
                command.execute();
                assertThat(command.isExecutable()).isFalse();
                stages.forEach(stage -> { assertThat(stage.isShowing()).isFalse(); assertThat(stage.getScene()).isNull(); });
                if (!duringStage) verify(view).close();
            } finally { command.close(); stages.forEach(Stage::hide); }
        });
    }

    @Test
    void fxmlFailureReleasesTheControllerCreatedBeforeLoadingAndRestoresRetry(FxRobot robot) {
        robot.interact(() -> {
            var helper = mock(AppHelper.class); when(helper.getExecutor()).thenReturn(task -> {});
            var view = spy(new AboutView());
            doThrow(new IllegalStateException("about initialization failed")).when(view).initialize(any(), any());
            var stages = new ArrayList<Stage>();
            var command = MainViewFactory.about(helper, () -> view, () -> { var stage = new Stage(); stages.add(stage); return stage; });
            try {
                assertThatThrownBy(command::execute).hasStackTraceContaining("about initialization failed");
                verify(view).close();
                assertThat(command.isExecutable()).isTrue();
                stages.forEach(stage -> { assertThat(stage.getScene()).isNull(); assertThat(stage.isShowing()).isFalse(); });
            } finally { command.close(); stages.forEach(Stage::hide); }
        });
    }
}
