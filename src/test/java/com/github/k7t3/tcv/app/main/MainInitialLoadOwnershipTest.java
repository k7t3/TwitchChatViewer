package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.service.LiveStateNotificator;

import com.github.k7t3.tcv.app.core.ClientSessions;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.event.ClientLoadedEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import de.saxsys.mvvmfx.MvvmFX;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainInitialLoadOwnershipTest {
    @ParameterizedTest(name = "logout error owner after close={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void logoutFailureUsesTheOwningApplicationUnlessMainHasClosed(boolean close, FxRobot robot) {
        var reference = new AtomicReference<MainViewModel>();
        var owner = new AtomicReference<javafx.stage.Stage>();
        var errors = new AtomicReference<org.mockito.MockedStatic<com.github.k7t3.tcv.app.core.ExceptionHandler>>();
        var result = new CompletableFuture<Void>();
        var failure = new IllegalStateException("logout cleanup failed");
        try {
            robot.interact(() -> {
                errors.set(mockStatic(com.github.k7t3.tcv.app.core.ExceptionHandler.class));
                owner.set(new javafx.stage.Stage());
                reference.set(main(FXTask.task(() -> {}), mock(ChannelViewModelRepository.class),
                        mock(TwitchChannelListViewModel.class), owner.get(),
                        helper -> when(helper.logoutAsync(any())).thenReturn(result)));
                MvvmFX.getNotificationCenter().publish(LoginEvent.class.getName(), new LoginEvent());
                reference.get().logout();
                if (close) reference.get().close();
                result.completeExceptionally(failure);
            });
            robot.interact(() -> {
                errors.get().verify(() -> com.github.k7t3.tcv.app.core.ExceptionHandler.handle(owner.get(), failure),
                        times(close ? 0 : 1));
                errors.get().verifyNoMoreInteractions();
            });
        } finally {
            robot.interact(() -> {
                if (reference.get() != null) reference.get().close();
                if (errors.get() != null) errors.get().close();
                if (owner.get() != null) owner.get().close();
            });
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"close,0", "logout,0", "login,1"})
    void anAlreadyCompletedLoadPublishesOnlyForTheCurrentLogin(String transition, int expected, FxRobot robot) {
        var reference = new AtomicReference<MainViewModel>();
        var channels = mock(TwitchChannelListViewModel.class);
        var repository = mock(ChannelViewModelRepository.class);
        var load = FXTask.task(() -> {});
        var received = new ArrayList<ClientLoadedEvent>();
        TypeSafeNotificationObserver<ClientLoadedEvent> observer = received::add;
        try {
            robot.interact(() -> {
                MvvmFX.getNotificationCenter().subscribe(ClientLoadedEvent.class.getName(), observer);
                reference.set(main(load, repository, channels));
                MvvmFX.getNotificationCenter().publish(LoginEvent.class.getName(), new LoginEvent());
                load.run(); assertThat(load.isDone()).isTrue();
                if (transition.equals("close")) reference.get().close();
                else if (transition.equals("logout")) reference.get().onLogout();
                else MvvmFX.getNotificationCenter().publish(LoginEvent.class.getName(), new LoginEvent());
            });
            robot.interact(() -> {
                verify(channels, times(expected)).bindChannels(any());
                assertThat(received).hasSize(expected);
            });
        } finally {
            robot.interact(() -> {
                if (reference.get() != null) reference.get().close();
                MvvmFX.getNotificationCenter().unsubscribe(ClientLoadedEvent.class.getName(), observer);
            });
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"current,1", "close,0", "logout,0"})
    void onlyTheCurrentLoginCanDisplayAnAlreadyCompletedFailure(String transition, int expected, FxRobot robot) {
        var reference = new AtomicReference<MainViewModel>();
        var owner = new AtomicReference<javafx.stage.Stage>();
        var errors = new AtomicReference<org.mockito.MockedStatic<com.github.k7t3.tcv.app.core.ExceptionHandler>>();
        var failure = new IllegalStateException("initial channels unavailable");
        var load = FXTask.<Void>task(() -> { throw failure; });
        try {
            robot.interact(() -> {
                errors.set(mockStatic(com.github.k7t3.tcv.app.core.ExceptionHandler.class));
                owner.set(new javafx.stage.Stage());
                reference.set(main(load, mock(ChannelViewModelRepository.class), mock(TwitchChannelListViewModel.class), owner.get()));
                MvvmFX.getNotificationCenter().publish(LoginEvent.class.getName(), new LoginEvent());
                load.run();
                if (transition.equals("close")) reference.get().close();
                else if (transition.equals("logout")) reference.get().onLogout();
            });
            robot.interact(() -> {
                errors.get().verify(() -> com.github.k7t3.tcv.app.core.ExceptionHandler.handle(owner.get(), failure), times(expected));
                errors.get().verifyNoMoreInteractions();
            });
        } finally {
            robot.interact(() -> {
                if (reference.get() != null) reference.get().close();
                if (errors.get() != null) errors.get().close();
                if (owner.get() != null) owner.get().close();
            });
        }
    }

    private static MainViewModel main(FXTask<?> load, ChannelViewModelRepository repository, TwitchChannelListViewModel channels) {
        return main(load, repository, channels, null);
    }

    private static MainViewModel main(FXTask<?> load, ChannelViewModelRepository repository, TwitchChannelListViewModel channels,
                                      javafx.stage.Stage owner) {
        return main(load, repository, channels, owner, helper -> {});
    }

    private static MainViewModel main(FXTask<?> load, ChannelViewModelRepository repository, TwitchChannelListViewModel channels,
                                      javafx.stage.Stage owner, Consumer<ClientSessions> configure) {
        when(channels.getSelectedChannels()).thenReturn(FXCollections.observableArrayList());
        when(repository.getChannels()).thenReturn(FXCollections.observableArrayList());
        doReturn(load).when(repository).loadAllAsync();
        var clips = mock(PostedClipRepository.class);
        when(clips.getCountBinding()).thenReturn(Bindings.size(FXCollections.observableHashMap()));
        var helper = mock(ClientSessions.class);
        when(helper.getChannelRepository()).thenReturn(mock(ChannelRepository.class));
        when(helper.getTwitch()).thenReturn(mock(Twitch.class));
        when(helper.userNameProperty()).thenReturn(new ReadOnlyStringWrapper("user").getReadOnlyProperty());
        when(helper.authorizedProperty()).thenReturn(new ReadOnlyBooleanWrapper(true).getReadOnlyProperty());
        configure.accept(helper);
        return new MainViewModel(helper, mock(EventSubscribers.class), repository, mock(ChatRoomContainerViewModel.class), channels, clips,
                new LiveStateNotificator(repository,
                        mainFailure -> com.github.k7t3.tcv.app.core.ExceptionHandler.handle(owner, mainFailure)),
                store -> { throw new AssertionError("authentication was not requested"); },
                mainFailure -> com.github.k7t3.tcv.app.core.ExceptionHandler.handle(owner, mainFailure));
    }
}
