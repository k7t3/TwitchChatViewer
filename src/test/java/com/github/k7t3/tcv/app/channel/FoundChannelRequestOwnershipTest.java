package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.image.ImageTestDoubles;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class FoundChannelRequestOwnershipTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "profile", "");

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLateSuccessCannotOpenChatAfterItsResultModelEnds(boolean logout, FxRobot robot) throws Exception {
        var events = new ArrayList<ChatOpeningEvent>();
        var source = FXTask.task(() -> mock(TwitchChannelViewModel.class));
        var result = new AtomicReference<FXTask<?>>();
        var model = new AtomicReference<FoundChannelViewModel>();
        robot.interact(() -> {
            var repository = mock(ChannelViewModelRepository.class);
            when(repository.getChannelAsync(OWNER)).thenReturn(source);
            model.set(model(repository, events));
            result.set(model.get().joinChatAsync());
            if (logout) model.get().onLogout(); else model.get().close();
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(events).isEmpty();
                assertThat(result.get().isCancelled()).isTrue();
                assertThat(source.isCancelled()).isFalse();
                assertThatThrownBy(model.get()::joinChatAsync).isInstanceOf(IllegalStateException.class);
            });
        } finally { robot.interact(model.get()::close); }
    }

    @Test
    void repeatedClicksShareOneOpeningAndDoNotAttachDuplicateSuccessCallbacks(FxRobot robot) throws Exception {
        var events = new ArrayList<ChatOpeningEvent>();
        var source = FXTask.task(() -> mock(TwitchChannelViewModel.class));
        var repository = mock(ChannelViewModelRepository.class);
        when(repository.getChannelAsync(OWNER)).thenReturn(source);
        var model = new AtomicReference<FoundChannelViewModel>();
        robot.interact(() -> {
            model.set(model(repository, events));
            assertThat(model.get().joinChatAsync()).isSameAs(model.get().joinChatAsync());
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(events).hasSize(1);
                assertThat(events.getFirst().getChannels()).containsExactly(source.getValue());
                verify(repository).getChannelAsync(OWNER);
            });
        } finally { robot.interact(model.get()::close); }
    }

    @Test
    void replacingTheResultCannotOpenThePreviousBroadcaster(FxRobot robot) throws Exception {
        var events = new ArrayList<ChatOpeningEvent>();
        var source = FXTask.task(() -> mock(TwitchChannelViewModel.class));
        var repository = mock(ChannelViewModelRepository.class);
        when(repository.getChannelAsync(OWNER)).thenReturn(source);
        var model = new AtomicReference<FoundChannelViewModel>();
        robot.interact(() -> {
            model.set(model(repository, events)); model.get().joinChatAsync();
            model.get().update(new FoundChannel(new Broadcaster("456", "next", "Next", "next-profile", ""), false, "Next game"));
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(events).isEmpty());
        } finally { robot.interact(model.get()::close); }
    }

    @Test
    void closingOneConsumerDoesNotCancelTheSharedRepositoryRequestForAnother(FxRobot robot) throws Exception {
        var oldEvents = new ArrayList<ChatOpeningEvent>(); var nextEvents = new ArrayList<ChatOpeningEvent>();
        var source = FXTask.task(() -> mock(TwitchChannelViewModel.class));
        var repository = mock(ChannelViewModelRepository.class);
        when(repository.getChannelAsync(OWNER)).thenReturn(source);
        var models = new ArrayList<FoundChannelViewModel>();
        var nextResult = new AtomicReference<FXTask<?>>();
        robot.interact(() -> {
            models.add(model(repository, oldEvents)); models.add(model(repository, nextEvents));
            models.getFirst().joinChatAsync(); nextResult.set(models.getLast().joinChatAsync());
            models.getFirst().close();
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS); nextResult.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(source.isCancelled()).isFalse();
                assertThat(oldEvents).isEmpty();
                assertThat(nextEvents).hasSize(1);
            });
        } finally { robot.interact(() -> models.forEach(FoundChannelViewModel::close)); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anEndedResultCannotStartAnotherProfileLoad(boolean logout, FxRobot robot) {
        robot.interact(() -> {
            var images = ImageTestDoubles.emptyLoader();
            var model = new FoundChannelViewModel(mock(ChannelViewModelRepository.class), new FoundChannel(OWNER, true, "Game"), images, failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class));
            if (logout) model.onLogout(); else model.close();
            clearInvocations(images);
            try {
                assertThatThrownBy(() -> model.update(new FoundChannel(OWNER, false, "Other"))).isInstanceOf(IllegalStateException.class);
                verifyNoInteractions(images);
            } finally { model.close(); }
        });
    }

    @Test
    void aRepositoryCancellationFinishesTheOwnedObserverWithoutFxDelivery(FxRobot robot) {
        var source = FXTask.task(() -> mock(TwitchChannelViewModel.class));
        var repository = mock(ChannelViewModelRepository.class); when(repository.getChannelAsync(OWNER)).thenReturn(source);
        var model = new AtomicReference<FoundChannelViewModel>(); var result = new AtomicReference<FXTask<?>>();
        robot.interact(() -> { model.set(model(repository, new ArrayList<>())); result.set(model.get().joinChatAsync()); });
        try {
            source.cancel(true);
            assertThatThrownBy(() -> result.get().get(5, TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.CancellationException.class);
        } finally { robot.interact(model.get()::close); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyAnActiveObserverReportsTheOriginalRepositoryFailure(boolean closed, FxRobot robot) {
        var failure = new IllegalStateException("channel unavailable");
        var source = FXTask.<TwitchChannelViewModel>task(() -> { throw failure; });
        var repository = mock(ChannelViewModelRepository.class); when(repository.getChannelAsync(OWNER)).thenReturn(source);
        var errors = new ArrayList<Throwable>(); var model = new AtomicReference<FoundChannelViewModel>();
        var result = new AtomicReference<FXTask<?>>();
        robot.interact(() -> {
            model.set(new FoundChannelViewModel(repository, new FoundChannel(OWNER, true, "Game"), ImageTestDoubles.emptyLoader(), errors::add, mock(BrowserActions.class)));
            result.set(model.get().joinChatAsync()); if (closed) model.get().close();
        });
        try {
            source.run();
            if (closed) assertThatThrownBy(() -> result.get().get(5, TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.CancellationException.class);
            else assertThatThrownBy(() -> result.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> { if (closed) assertThat(errors).isEmpty(); else assertThat(errors).containsExactly(failure); });
        } finally { robot.interact(model.get()::close); }
    }

    private static FoundChannelViewModel model(ChannelViewModelRepository repository, List<ChatOpeningEvent> events) {
        var model = spy(new FoundChannelViewModel(repository, new FoundChannel(OWNER, true, "Game"), ImageTestDoubles.emptyLoader(), failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class)));
        doAnswer(invocation -> { events.add(invocation.getArgument(0)); return null; }).when(model).publish(any(ChatOpeningEvent.class));
        return model;
    }
}
