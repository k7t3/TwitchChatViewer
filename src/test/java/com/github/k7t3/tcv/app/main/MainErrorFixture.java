package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.core.ClientSessions;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import java.awt.Desktop;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import org.mockito.MockedConstruction;
import org.testfx.api.FxRobot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

final class MainErrorFixture implements AutoCloseable {
    final FxRobot robot;
    final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    final Stage owner = mock(Stage.class);
    final CountDownLatch displayed = new CountDownLatch(1);
    final MainViewModel model;
    final MockedConstruction<Alert> alerts;

    MainErrorFixture(FxRobot robot) {
        this.robot = robot;
        var models = new AtomicReference<MainViewModel>();
        var dialogs = new AtomicReference<MockedConstruction<Alert>>();
        try {
            robot.interact(() -> {
                dialogs.set(mockConstruction(Alert.class, (alert, context) -> {
                    when(alert.getDialogPane()).thenReturn(new DialogPane());
                    when(alert.showAndWait()).thenAnswer(call -> {
                        displayed.countDown(); return Optional.empty();
                    });
                }));
                var helper = mock(AppHelper.class);
        when(helper.getClientSessions()).thenReturn(mock(ClientSessions.class));
                when(helper.getPrimaryStage()).thenReturn(owner);
                when(helper.getExecutor()).thenReturn(queue::add);
                when(helper.getSubscribers()).thenReturn(mock(EventSubscribers.class));
                models.set(MainModelFactory.create(helper, mock(ChannelGroupRepository.class), mock(ImageLoader.class), mainFailure -> com.github.k7t3.tcv.app.core.ExceptionHandler.handle(owner, mainFailure)));
            });
        } catch (RuntimeException | Error failure) {
            try {
                robot.interact(() -> {
                    try { if (models.get() != null) models.get().close(); }
                    finally { if (dialogs.get() != null) dialogs.get().close(); }
                });
            } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
        model = models.get();
        alerts = dialogs.get();
    }

    void failNextBrowserCall(Throwable failure) {
        robot.interact(() -> {
            assertThat(queue).hasSize(1);
            try (var desktop = mockStatic(Desktop.class)) {
                desktop.when(Desktop::getDesktop).thenThrow(failure);
                queue.remove().run();
                desktop.verify(Desktop::getDesktop);
            }
        });
    }

    void verifyErrorOwner() throws InterruptedException {
        assertThat(displayed.await(5, TimeUnit.SECONDS)).isTrue();
        robot.interact(() -> {
            assertThat(alerts.constructed()).hasSize(1);
            verify(alerts.constructed().getFirst()).initOwner(owner);
            verify(alerts.constructed().getFirst()).showAndWait();
            verify(owner, never()).close();
        });
    }

    @Override public void close() {
        try {
            robot.interact(model::close);
            Runnable pending;
            while ((pending = queue.poll()) != null) pending.run();
        } finally { robot.interact(alerts::close); }
    }
}
