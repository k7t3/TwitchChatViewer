package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class GroupChannelRetentionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removingMembershipReleasesRetentionOnlyAfterASuccessfulCommit(boolean fail, FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            var group = fixture.group();
            robot.interact(() -> fixture.repository.saveAsync(group));
            fixture.runIo();
            if (fail) doThrow(new IllegalStateException("membership update failed")).when(fixture.service).save(any(), any());
            robot.interact(() -> {
                group.getChannels().clear();
                fixture.repository.saveAsync(group);
                assertThat(fixture.channel.isPersistent()).isTrue();
            });
            fixture.runIo();
            robot.interact(() -> {
                assertThat(fixture.channel.isPersistent()).isEqualTo(fail);
                assertThat(group.getChannels().contains(fixture.channel)).isEqualTo(fail);
            });
        }
    }

    @Test
    void aRetentionListenerEndingTheScopeCannotLeaveARetainedChannelOrDropTheAcceptedWrite(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            robot.interact(() -> {
                fixture.channel.persistentProperty().addListener((observable, old, value) -> {
                    if (value) fixture.repository.close();
                });
                fixture.repository.saveAsync(fixture.group());
                assertThat(fixture.repository.isClosed()).isTrue();
                assertThat(fixture.channel.isPersistent()).isFalse();
            });
            fixture.runIo();
            verify(fixture.service).save(any(), any());
            robot.interact(() -> assertThat(fixture.repository.getAll()).isEmpty());
        }
    }

    @Test
    void endingTheScopeDuringMetadataPublicationCannotReinsertTheOldGroup(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            var group = fixture.group();
            robot.interact(() -> {
                group.createdAtProperty().addListener((observable, old, value) -> fixture.repository.close());
                fixture.repository.saveAsync(group);
            });
            fixture.runIo();
            robot.interact(() -> {
                assertThat(fixture.repository.isClosed()).isTrue();
                assertThat(fixture.repository.getAll()).isEmpty();
                assertThat(fixture.channel.isPersistent()).isFalse();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPendingCreateRetainsItsChannelAndDeletionKeepsOnlyFollowedChannels(boolean following, FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, following)) {
            var group = fixture.group();
            robot.interact(() -> {
                fixture.repository.saveAsync(group);
                assertThat(fixture.channel.isPersistent()).isTrue();
            });
            fixture.runIo();
            robot.interact(() -> {
                assertThat(fixture.channel.isPersistent()).isTrue();
                fixture.repository.removeAsync(group);
                assertThat(fixture.channel.isPersistent()).isTrue();
            });
            fixture.runIo();
            robot.interact(() -> assertThat(fixture.channel.isPersistent()).isEqualTo(following));
        }
    }

    @Test
    void aFailedCreateReleasesItsTemporaryRetention(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            doThrow(new IllegalStateException("save failed")).when(fixture.service).save(any(), any());
            robot.interact(() -> fixture.repository.saveAsync(fixture.group()));
            fixture.runIo();
            robot.interact(() -> assertThat(fixture.channel.isPersistent()).isFalse());
        }
    }

    @Test
    void deletingOneGroupCannotReleaseAChannelStillInAnotherCommittedGroup(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            var first = fixture.group(); var second = fixture.group();
            robot.interact(() -> { fixture.repository.saveAsync(first); fixture.repository.saveAsync(second); });
            fixture.runIo();
            robot.interact(() -> fixture.repository.removeAsync(first));
            fixture.runIo();
            robot.interact(() -> {
                assertThat(fixture.channel.isPersistent()).isTrue();
                fixture.repository.removeAsync(second);
            });
            fixture.runIo();
            robot.interact(() -> assertThat(fixture.channel.isPersistent()).isFalse());
        }
    }

    @Test
    void logoutReleasesPendingRetentionAndLateSuccessCannotRestoreIt(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            robot.interact(() -> {
                fixture.repository.saveAsync(fixture.group());
                fixture.repository.onLogout();
                assertThat(fixture.channel.isPersistent()).isFalse();
            });
            fixture.runIo();
            robot.interact(() -> assertThat(fixture.channel.isPersistent()).isFalse());
        }
    }

    @Test
    void aClosedChannelCannotBeRetainedByANewGroup(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot, false)) {
            robot.interact(() -> {
                fixture.channel.close();
                assertThatThrownBy(() -> fixture.repository.saveAsync(fixture.group())).isInstanceOf(IllegalStateException.class);
            });
            assertThat(fixture.scheduled).isEmpty();
            verify(fixture.service, never()).save(any(), any());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final FxRobot robot;
        final ChannelGroupEntityService service = mock(ChannelGroupEntityService.class);
        final LinkedBlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        final java.util.concurrent.ExecutorService pool = Executors.newSingleThreadExecutor();
        final ChannelGroupRepository repository;
        final TwitchChannelViewModel channel;
        Fixture(FxRobot robot, boolean following) {
            this.robot = robot;
            var domain = mock(TwitchChannel.class);
            when(domain.getBroadcaster()).thenReturn(new Broadcaster("123", "user", "User", "", ""));
            when(domain.isFollowing()).thenReturn(following);
            var retained = new AtomicBoolean(following);
            when(domain.isPersistent()).thenAnswer(invocation -> retained.get());
            doAnswer(invocation -> { retained.set(invocation.getArgument(0)); return null; }).when(domain).setPersistent(anyBoolean());
            channel = new TwitchChannelViewModel(domain, mock(ChannelViewModelRepository.class), mock(ImageLoader.class), pool, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            repository = new ChannelGroupRepository(service, scheduled::add, ignored -> {}, error -> {});
        }
        ChannelGroup group() { var group = new ChannelGroup(); group.setName("group"); group.getChannels().add(channel); return group; }
        void runIo() throws Exception {
            var pump = scheduled.poll(5, TimeUnit.SECONDS); assertThat(pump).isNotNull();
            pool.submit(pump).get(5, TimeUnit.SECONDS); robot.interact(() -> {});
        }
        @Override public void close() {
            robot.interact(() -> { repository.close(); channel.close(); });
            Runnable job; while ((job = scheduled.poll()) != null) {
                try { pool.submit(job).get(5, TimeUnit.SECONDS); } catch (Exception failure) { throw new AssertionError(failure); }
            }
            pool.close();
        }
    }
}
