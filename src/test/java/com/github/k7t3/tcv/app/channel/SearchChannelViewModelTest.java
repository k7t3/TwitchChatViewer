package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.MvvmFX;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.k7t3.tcv.app.image.ImageTestDoubles.emptyLoader;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class SearchChannelViewModelTest {
    private final TaskWorker worker = new TaskWorker();
    private final List<SearchChannelViewModel> models = new ArrayList<>();

    private SearchChannelViewModel createModel() {
        var model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), emptyLoader(), worker.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        models.add(model);
        return model;
    }

    @AfterEach
    void releaseModelsAndWorker(FxRobot robot) {
        try { robot.interact(() -> CloseActions.runAll(models.stream().map(model -> (Runnable) model::close).toArray(Runnable[]::new))); }
        finally { worker.close(); }
    }

    @Test
    void closedModelCannotStartAnotherSearch(FxRobot robot) {
        robot.interact(() -> {
            var model = createModel();
            model.close();
            assertThatThrownBy(() -> model.search(TimeUnit.MILLISECONDS, 0)).isInstanceOf(IllegalStateException.class);
            assertThat(model.isLoading()).isFalse();
        });
    }

    @Test
    void aSessionChangeCancelsThePreviousSearch(FxRobot robot) throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var api = mock(TwitchAPI.class);
        var client = mock(Twitch.class);
        when(client.getTwitchAPI()).thenReturn(api);
        when(api.search("previous", true)).thenAnswer(invocation -> {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { }
            finally { finished.countDown(); }
            return List.of(new FoundChannel(new Broadcaster("old", "old", "Old", "file:/old", ""), true, "Game"));
        });
        var model = createModel();
        try {
            robot.interact(() -> {
                model.setTwitch(client);
                model.setKeyword("previous");
                model.search(TimeUnit.MILLISECONDS, 0);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                model.setTwitch(mock(Twitch.class));
                assertThat(model.isLoading()).isFalse();
            });
        } finally {
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> { model.close(); assertThat(model.getChannels()).isEmpty(); });
        }
    }

    @Test
    void aResultCleanupFailureCannotKeepOtherResultsOrTheSessionBinding(FxRobot robot) {
        robot.interact(() -> {
            var model = createModel();
            var source = new SimpleObjectProperty<Twitch>(mock(Twitch.class));
            model.twitchProperty().bind(source);
            var first = mock(FoundChannelViewModel.class);
            var second = mock(FoundChannelViewModel.class);
            var failure = new IllegalStateException("cannot release profile image");
            doThrow(failure).when(first).close();
            model.getChannels().setAll(first, second);
            assertThatThrownBy(model::close).isSameAs(failure);
            verify(second).close();
            assertThat(model.getChannels()).isEmpty();
            assertThat(model.twitchProperty().isBound()).isFalse();
            assertThat(model.getTwitch()).isNull();
        });
    }

    @Test
    void closeReleasesBoundTwitchSession(FxRobot robot) {
        robot.interact(() -> {
            var session = new SimpleObjectProperty<Twitch>(mock(Twitch.class));
            var model = createModel();
            model.twitchProperty().bind(session);

            model.close();
            session.set(mock(Twitch.class));

            assertThat(model.getTwitch()).isNull();
        });
    }

    @Test
    void closeReleasesResultViewModels(FxRobot robot) {
        robot.interact(() -> {
            var model = createModel();
            var broadcaster = new Broadcaster("123", "channel", "Channel", "file:/missing-profile.png", "");
            var item = new FoundChannelViewModel(mock(ChannelViewModelRepository.class),
                    new FoundChannel(broadcaster, true, "Game"), emptyLoader(), failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class));
            var received = new ArrayList<ProbeEvent>();
            try {
                model.getChannels().add(item);
                item.subscribe(ProbeEvent.class, received::add);

                model.close();
                MvvmFX.getNotificationCenter().publish(ProbeEvent.class.getName(), new ProbeEvent());

                assertThat(received).isEmpty();
            } finally {
                item.close();
                model.close();
            }
        });
    }

    private record ProbeEvent() implements AppEvent {
    }

    @Test
    void createsSearchResultViewModelsOnFxThread(FxRobot robot) throws Exception {
        var threads = new CopyOnWriteArrayList<Boolean>();
        var updated = new CountDownLatch(1);
        var broadcaster = new Broadcaster("123", "channel", "Channel", "file:/missing-profile.png", "");
        var found = mock(FoundChannel.class);
        when(found.getBroadcaster()).thenAnswer(invocation -> {
            threads.add(Platform.isFxApplicationThread());
            return broadcaster;
        });
        when(found.getGameName()).thenReturn("Game");
        when(found.isLive()).thenReturn(true);
        var api = mock(TwitchAPI.class);
        var twitch = mock(Twitch.class);
        when(twitch.getTwitchAPI()).thenReturn(api);
        when(api.search("channel", true)).thenReturn(List.of(found));
        var model = createModel();
        try {
            robot.interact(() -> {
                model.getChannels().addListener((ListChangeListener<FoundChannelViewModel>) change -> updated.countDown());
                model.setTwitch(twitch);
                model.setKeyword("channel");
                model.search(TimeUnit.SECONDS, 0);
            });
            assertThat(updated.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(threads).isNotEmpty().containsOnly(true);
            robot.interact(() -> assertThat(model.getChannels())
                    .extracting(item -> item.getBroadcaster().getUserId()).containsExactly("123"));
        } finally {
            robot.interact(() -> {
                model.getChannels().forEach(FoundChannelViewModel::close);
                model.close();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavingScreenCancelsRunningSearch(boolean logout, FxRobot robot) throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var api = mock(TwitchAPI.class);
        var twitch = mock(Twitch.class);
        when(twitch.getTwitchAPI()).thenReturn(api);
        when(api.search("channel", true)).thenAnswer(invocation -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // 実 HTTP 呼び出しがキャンセル後に戻る状況を再現する。
            } finally {
                finished.countDown();
            }
            return List.of();
        });
        var model = createModel();
        try {
            robot.interact(() -> {
                model.setTwitch(twitch);
                model.setKeyword("channel");
                model.search(TimeUnit.SECONDS, 0);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            robot.interact(() -> {
                if (logout) model.onLogout();
                else model.close();
            });

            robot.interact(() -> assertThat(model.isLoading()).isFalse());
        } finally {
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(model::close);
        }
    }
}
