package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AppHelperErrorOwnerTest {
    @Test
    void aFilterSaveFailureUsesTheApplicationsOwnWindow(FxRobot robot) throws Exception {
        var pending = new LinkedBlockingQueue<Runnable>();
        var worker = mock(TaskWorker.class);
        when(worker.getExecutor()).thenReturn(pending::add);
        var owner = mock(Stage.class);
        var helper = new AppHelper(worker, new EventPublishers(), appFailure -> ExceptionHandler.handle(owner, appFailure));
        var file = mock(UserDataFile.class);
        when(file.getConnector()).thenReturn(mock(DBConnector.class));
        helper.setUserDataFile(file);
        var failure = new IllegalStateException("filter storage unavailable");
        var displayed = new CountDownLatch(1);
        var stores = new AtomicReference<MockedConstruction<ChatFilterEntityStore>>();
        var alerts = new AtomicReference<MockedConstruction<Alert>>();
        var request = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> {
                helper.setPrimaryStage(owner);
                stores.set(mockConstruction(ChatFilterEntityStore.class, (store, context) ->
                        when(store.save(any(), any())).thenThrow(failure)));
                alerts.set(mockConstruction(Alert.class, (alert, context) -> {
                    when(alert.getDialogPane()).thenReturn(new DialogPane());
                    when(alert.showAndWait()).thenAnswer(call -> {
                        displayed.countDown(); return Optional.empty();
                    });
                }));

                request.set(helper.getChatFilters().saveKeywordFilter(KeywordFilterEntry.containsMatch("blocked")));
            });
            var work = pending.poll(5, TimeUnit.SECONDS);
            assertThat(work).as("accepted filter save").isNotNull();
            work.run();

            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(displayed.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                assertThat(alerts.get().constructed()).hasSize(1);
                verify(alerts.get().constructed().getFirst()).initOwner(owner);
                verify(alerts.get().constructed().getFirst()).showAndWait();
                verify(owner, never()).close();
            });
            verify(stores.get().constructed().getFirst()).save(any(), any());
        } finally {
            try {
                robot.interact(helper::prepareShutdown);
                Runnable remaining;
                while ((remaining = pending.poll()) != null) remaining.run();
                helper.close();
            } finally {
                robot.interact(() -> {
                    try { if (stores.get() != null) stores.get().close(); }
                    finally { if (alerts.get() != null) alerts.get().close(); }
                });
            }
        }
    }

    @Test
    void aGroupSaveFailureUsesTheApplicationsOwnWindow(FxRobot robot) throws Exception {
        var pending = new LinkedBlockingQueue<Runnable>();
        var worker = mock(TaskWorker.class);
        when(worker.getExecutor()).thenReturn(pending::add);
        var owner = mock(Stage.class);
        var helper = new AppHelper(worker, new EventPublishers(), appFailure -> ExceptionHandler.handle(owner, appFailure));
        var file = mock(UserDataFile.class);
        when(file.getConnector()).thenReturn(mock(DBConnector.class));
        helper.setUserDataFile(file);
        var failure = new IllegalStateException("group storage unavailable");
        var displayed = new CountDownLatch(1);
        var services = new AtomicReference<MockedConstruction<ChannelGroupEntityService>>();
        var alerts = new AtomicReference<MockedConstruction<Alert>>();
        var request = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> {
                helper.setPrimaryStage(owner);
                services.set(mockConstruction(ChannelGroupEntityService.class, (service, context) ->
                        doThrow(failure).when(service).save(any(), any())));
                alerts.set(mockConstruction(Alert.class, (alert, context) -> {
                    when(alert.getDialogPane()).thenReturn(new DialogPane());
                    when(alert.showAndWait()).thenAnswer(call -> {
                        displayed.countDown(); return Optional.empty();
                    });
                }));
                var group = new ChannelGroup();
                group.setName("draft");

                request.set(helper.getChannelGroupRepository().saveAsync(group));
            });
            var work = pending.poll(5, TimeUnit.SECONDS);
            assertThat(work).as("accepted group save").isNotNull();
            work.run();

            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(displayed.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                assertThat(alerts.get().constructed()).hasSize(1);
                verify(alerts.get().constructed().getFirst()).initOwner(owner);
                verify(alerts.get().constructed().getFirst()).showAndWait();
                verify(owner, never()).close();
            });
            verify(services.get().constructed().getFirst()).save(any(), any());
        } finally {
            try {
                robot.interact(helper::prepareShutdown);
                Runnable remaining;
                while ((remaining = pending.poll()) != null) remaining.run();
                try { helper.close(); }
                catch (RuntimeException | Error releaseFailure) {
                    if (releaseFailure != failure) throw releaseFailure;
                }
            } finally {
                robot.interact(() -> {
                    try { if (services.get() != null) services.get().close(); }
                    finally { if (alerts.get() != null) alerts.get().close(); }
                });
            }
        }
    }
}
