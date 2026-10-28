package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import de.saxsys.mvvmfx.MvvmFX;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



@ExtendWith(ApplicationExtension.class)
class AppHelperModelLifetimeTest {
    @TempDir Path directory;

    @Test
    void aCredentialFreezeFailureCannotSkipTheSharedUiModels(FxRobot robot) throws Exception {
        var worker = mock(TaskWorker.class);
        when(worker.getExecutor()).thenReturn(Runnable::run);
        var helper = new AppHelper(worker, new EventPublishers(),
                client -> mock(com.github.k7t3.tcv.domain.channel.ChannelRepository.class),
                failure -> { throw new AssertionError(failure); });
        var file = new UserDataFile(directory.resolve("failed-preparation.db"));
        helper.setUserDataFile(file);
        file.connectDatabase(DatabaseVersion.EMPTY);
        var twitch = mock(com.github.k7t3.tcv.domain.Twitch.class);
        var groups = new AtomicReference<ChannelGroupRepository>();
        var filters = new AtomicReference<ChatFilters>();
        var received = new ArrayList<Probe>();
        var failure = new IllegalStateException("cannot stop credential updates");
        try {
            robot.interact(() -> {
                helper.setTwitch(twitch);
                groups.set(helper.getChannelGroupRepository());
                filters.set(helper.getChatFilters());
                groups.get().subscribe(Probe.class, received::add);
                filters.get().subscribe(Probe.class, received::add);
                org.mockito.Mockito.doThrow(failure).when(twitch).freezeCredentials();

                assertThatThrownBy(helper::prepareShutdown).isSameAs(failure);

                assertThat(groups.get().isClosed()).isTrue();
                assertThat(filters.get().isClosed()).isTrue();
                assertThat(helper.getTwitch()).isNull();
                assertThat(helper.isAuthorized()).isFalse();
                MvvmFX.getNotificationCenter().publish(Probe.class.getName(), new Probe());
                assertThat(received).isEmpty();
                assertThat(file.getConnector().isConnected()).isTrue();
            });
        } finally {
            org.mockito.Mockito.doNothing().when(twitch).freezeCredentials();
            try {
                robot.interact(() -> com.github.k7t3.tcv.lifecycle.CloseActions.runAll(
                        () -> { if (groups.get() != null) groups.get().close(); },
                        () -> { if (filters.get() != null) filters.get().close(); }));
            } finally { helper.close(); }
        }
        assertThat(file.getConnector().isConnected()).isFalse();
    }

    @Test
    void partiallyStartedServicesLoseTheirUiSubscriptionsBeforeIoRelease(FxRobot robot) throws Exception {
        var worker = mock(TaskWorker.class);
        when(worker.getExecutor()).thenReturn(Runnable::run);
        var helper = new AppHelper(worker, new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var file = new UserDataFile(directory.resolve("userdata.db"));
        helper.setUserDataFile(file);
        file.connectDatabase(DatabaseVersion.EMPTY);
        var groups = new AtomicReference<ChannelGroupRepository>();
        var filters = new AtomicReference<ChatFilters>();
        var received = new ArrayList<Probe>();
        try {
            robot.interact(() -> {
                groups.set(helper.getChannelGroupRepository());
                filters.set(helper.getChatFilters());
                groups.get().subscribe(Probe.class, received::add);
                filters.get().subscribe(Probe.class, received::add);
                helper.prepareShutdown();
                assertThat(groups.get().isClosed()).isTrue();
                assertThat(filters.get().isClosed()).isTrue();
                MvvmFX.getNotificationCenter().publish(Probe.class.getName(), new Probe());
                assertThat(received).isEmpty();
                assertThatThrownBy(helper::getChannelGroupRepository).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(helper::getChatFilters).isInstanceOf(IllegalStateException.class);
            });
        } finally { helper.close(); }
        assertThat(file.getConnector().isConnected()).isFalse();
    }

    private record Probe() implements AppEvent {}
}
