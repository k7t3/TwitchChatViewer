package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.service.FXTask;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;

@ExtendWith(ApplicationExtension.class)
class ChatContainerShutdownTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();


    @Test
    void closeKeepsFxThreadResponsiveWhileChatLeaves(FxRobot robot) throws Exception {
        var releaseLeave = new CountDownLatch(1);
        var leaveStarted = new CountDownLatch(1);
        var fxProgress = new CountDownLatch(1);
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class),
                () -> { throw new AssertionError("chat resources were not requested"); }, worker.getExecutor());
        FXTask<Void> leave = FXTask.task(() -> {
            leaveStarted.countDown();
            releaseLeave.await();
            return null;
        });
        var room = mock(ChatRoomViewModel.class);
        doReturn(leave).when(room).leaveChatAsync();
        try {
            robot.interact(() -> {
                model.getFloatingChatRoomList().add(room);
                worker.getExecutor().execute(leave);
            });
            assertThat(leaveStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Platform.runLater(model::close);
            Platform.runLater(fxProgress::countDown);

            assertThat(fxProgress.await(1, TimeUnit.SECONDS)).as("FX event following close").isTrue();
        } finally {
            releaseLeave.countDown();
            leave.get(5, TimeUnit.SECONDS);
            robot.interact(model::close);
        }
    }
}
