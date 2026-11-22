package com.github.k7t3.tcv.app.main;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Function;

import java.util.function.Consumer;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;

import com.github.k7t3.tcv.app.service.LiveStateNotificator;

import com.github.k7t3.tcv.app.core.ClientSessions;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import de.saxsys.mvvmfx.MvvmFX;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;



@ExtendWith(ApplicationExtension.class)
class MainViewModelLifecycleTest {

    @Test
    void authenticationReturnedAfterLogoutIsReleasedAndRejected(FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<MainViewModel>();
            var authentication = new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run,
                    failure -> { throw new AssertionError(failure); });
            var clips = new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {},
                    failure -> { throw new AssertionError(failure); });
            var model = main(helper(), mock(ChannelViewModelRepository.class), mock(ChatRoomContainerViewModel.class),
                    new TwitchChannelListViewModel(mock(BrowserActions.class)), clips,
                    store -> { reference.get().onLogout(); return authentication; },
                    failure -> { throw new AssertionError(failure); });
            reference.set(model);
            try {
                assertThatThrownBy(() -> model.createAuthenticator(mock(CredentialStore.class)))
                        .isInstanceOf(IllegalStateException.class).hasMessage("authentication request was superseded");
                assertThat(authentication.isClosed()).isTrue();
                assertThat(model.isClosed()).isFalse();
            } finally {
                try { model.close(); }
                finally { authentication.close(); }
            }
        });
    }

    @Test
    void aSupersededAuthenticationCannotReplaceTheNewerRequest(FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<MainViewModel>();
            var requests = new AtomicInteger();
            var previous = new AuthenticatorViewModel(mock(CredentialStore.class),
                    client -> {}, Runnable::run, failure -> { throw new AssertionError(failure); });
            var current = new AuthenticatorViewModel(mock(CredentialStore.class),
                    client -> {}, Runnable::run, failure -> { throw new AssertionError(failure); });
            var clips = new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {},
                    failure -> { throw new AssertionError(failure); });
            var model = main(helper(), mock(ChannelViewModelRepository.class), mock(ChatRoomContainerViewModel.class),
                    new TwitchChannelListViewModel(mock(BrowserActions.class)), clips, store -> {
                        if (requests.incrementAndGet() == 1) {
                            assertThat(reference.get().createAuthenticator(store)).isSameAs(current);
                            return previous;
                        }
                        return current;
                    }, failure -> { throw new AssertionError(failure); });
            reference.set(model);
            try {
                assertThatThrownBy(() -> model.createAuthenticator(mock(CredentialStore.class)))
                        .isInstanceOf(IllegalStateException.class).hasMessage("authentication request was superseded");
                assertThat(previous.isClosed()).isTrue();
                assertThat(current.isClosed()).isFalse();
                model.close();
                assertThat(current.isClosed()).isTrue();
            } finally {
                try { model.close(); }
                finally {
                    try { previous.close(); }
                    finally { current.close(); }
                }
            }
        });
    }

    @Test
    void authenticationReturnedAfterReentrantMainCloseIsReleasedAndRejected(FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<MainViewModel>();
            var authentication = new AuthenticatorViewModel(mock(CredentialStore.class),
                    client -> {}, Runnable::run, failure -> { throw new AssertionError(failure); });
            var clips = new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {},
                    failure -> { throw new AssertionError(failure); });
            var model = main(helper(), mock(ChannelViewModelRepository.class), mock(ChatRoomContainerViewModel.class),
                    new TwitchChannelListViewModel(mock(BrowserActions.class)), clips,
                    store -> { reference.get().close(); return authentication; },
                    failure -> { throw new AssertionError(failure); });
            reference.set(model);
            try {
                assertThatThrownBy(() -> model.createAuthenticator(mock(CredentialStore.class)))
                        .isInstanceOf(IllegalStateException.class).hasMessage("main view model is closed");
                assertThat(authentication.isClosed()).isTrue();
                assertThat(model.isClosed()).isTrue();
            } finally {
                try { model.close(); }
                finally { authentication.close(); }
            }
        });
    }

    @Test
    void partialInitializationStillClosesEveryCreatedChild(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelViewModelRepository.class);
            var chat = mock(ChatRoomContainerViewModel.class);
            var channels = new TwitchChannelListViewModel(mock(BrowserActions.class));
            var clips = mock(PostedClipRepository.class);
            var failure = new IllegalStateException("cannot bind clip count");
            Mockito.when(clips.getCountBinding()).thenThrow(failure);
            assertThatThrownBy(() -> main(helper(), repository, chat, channels, clips, unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); })).isSameAs(failure);
            verify(repository).close();
            verify(chat).close();
            verify(clips).close();
            assertThat(channels.isClosed()).isTrue();
        });
    }

    @Test
    void theLiveNotificatorSharesTheMainLifetimeAndKeepsItsOriginalEventSource(FxRobot robot) {
        robot.interact(() -> {
            var model = MainModelFactory.create(helper(), mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
            var notificator = model.getLiveNotificator();
            var received = new ArrayList<ProbeEvent>();
            notificator.subscribe(ProbeEvent.class, received::add);
            model.close();
            assertThat(notificator.isClosed()).isTrue();
            MvvmFX.getNotificationCenter().publish(ProbeEvent.class.getName(), new ProbeEvent());
            assertThat(received).isEmpty();
        });
    }

    @Test
    void replacingAuthenticationClosesThePreviousSession(FxRobot robot) {
        robot.interact(() -> {
            var model = MainModelFactory.create(helper(), mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
            try {
                var previous = model.createAuthenticator(mock(CredentialStore.class));

                var current = model.createAuthenticator(mock(CredentialStore.class));

                assertThat(previous.isClosed()).isTrue();
                assertThat(current.isClosed()).isFalse();
            } finally {
                model.close();
            }
        });
    }

    @Test
    void closingMainAlsoClosesItsActiveAuthentication(FxRobot robot) {
        robot.interact(() -> {
            var model = MainModelFactory.create(helper(), mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
            try {
                var authenticator = model.createAuthenticator(mock(CredentialStore.class));

                model.close();

                assertThat(authenticator.isClosed()).isTrue();
            } finally {
                model.close();
            }
        });
    }

    @Test
    void failingChildDoesNotPreventOtherChildrenFromClosing(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelViewModelRepository.class);
            var chat = mock(ChatRoomContainerViewModel.class);
            var channels = new TwitchChannelListViewModel(mock(BrowserActions.class));
            var clips = new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            var model = main(helper(), repository, chat, channels, clips, unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
            var failure = new IllegalStateException("chat cleanup failed");
            doThrow(failure).when(chat).close();
            var received = new ArrayList<ProbeEvent>();
            channels.subscribe(ProbeEvent.class, received::add);
            clips.subscribe(ProbeEvent.class, received::add);
            try {
                assertThatThrownBy(model::close).isSameAs(failure);

                verify(repository).close();
                MvvmFX.getNotificationCenter().publish(ProbeEvent.class.getName(), new ProbeEvent());
                assertThat(received).isEmpty();
            } finally {
                model.close();
                channels.close();
                clips.close();
            }
        });
    }

    @Test
    void closeReleasesOwnedViewModels(FxRobot robot) {
        var model = MainModelFactory.create(helper(), mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
        List<AbstractViewModel> children = List.of(model.getChannelRepository(), model.getChatContainer(),
                model.getChannelListViewModel(), model.getClipRepository());
        var received = new ArrayList<AbstractViewModel>();
        try {
            robot.interact(() -> {
                children.forEach(child -> child.subscribe(ProbeEvent.class, event -> received.add(child)));
                model.close();
                MvvmFX.getNotificationCenter().publish(ProbeEvent.class.getName(), new ProbeEvent());
            });

            robot.interact(() -> assertThat(received).isEmpty());
        } finally {
            robot.interact(() -> {
                model.close();
                children.forEach(AbstractViewModel::close);
            });
        }
    }

    private static MainViewModel main(AppHelper helper, ChannelViewModelRepository repository,
                                      ChatRoomContainerViewModel chat, TwitchChannelListViewModel channels,
                                      PostedClipRepository clips, Consumer<Throwable> failures) {
        return main(helper, repository, chat, channels, clips,
                store -> { throw new AssertionError("authentication was not requested"); }, failures);
    }

    private static MainViewModel main(AppHelper helper, ChannelViewModelRepository repository,
                                      ChatRoomContainerViewModel chat, TwitchChannelListViewModel channels,
                                      PostedClipRepository clips,
                                      Function<CredentialStore, AuthenticatorViewModel> authentication,
                                      Consumer<Throwable> failures) {
        return new MainViewModel(helper.getClientSessions(), helper.getSubscribers(), repository, chat, channels, clips,
                new LiveStateNotificator(repository, failures),
                authentication, failures);
    }

    private static AppHelper helper() {
        var helper = mock(AppHelper.class);
        when(helper.getClientSessions()).thenReturn(mock(ClientSessions.class));
        when(helper.getExecutor()).thenReturn(Runnable::run);
        when(helper.getSubscribers()).thenReturn(mock(EventSubscribers.class));
        return helper;
    }

    private record ProbeEvent() implements AppEvent {
    }
}
