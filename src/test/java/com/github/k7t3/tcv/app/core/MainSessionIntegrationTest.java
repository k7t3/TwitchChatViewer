package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.common.exception.UnauthorizedException;
import com.github.k7t3.tcv.domain.HelixClient;
import com.github.twitch4j.helix.domain.ClipList;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainSessionIntegrationTest {
    @TempDir Path directory;

    @Test
    void anActual401RefreshFailureEndsTheSameMainSessionAndRemovesOnlyItsCredential(FxRobot robot) throws Exception {
        var worker = mock(TaskWorker.class);
        var queue = new LinkedBlockingQueue<Runnable>();
        when(worker.getExecutor()).thenReturn(queue::add);
        var domain = mock(ChannelRepository.class);
        var helper = new AppHelper(worker, new EventPublishers(), client -> domain, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var file = new UserDataFile(directory.resolve("invalidated.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        helper.setUserDataFile(file);
        var model = MainModelFactory.create(helper, mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
        var own = new OAuth2Credential(
                "twitch", "own-token", "refresh", "123", "User", 3600, List.of());
        var backing = mock(CredentialStore.class);
        var saved = new AtomicReference<>(
                new CredentialStore.Snapshot("1", List.of(own)));
        when(backing.snapshot()).thenAnswer(invocation -> saved.get());
        when(backing.compareAndSet(any(), any(), any())).thenAnswer(invocation -> {
            CredentialStore.Snapshot expected = invocation.getArgument(0);
            BooleanSupplier permitted = invocation.getArgument(2);
            if (!expected.revision().equals(saved.get().revision()) || !permitted.getAsBoolean()) return Optional.empty();
            var next = new CredentialStore.Snapshot("2", invocation.getArgument(1));
            saved.set(next);
            return Optional.of(next);
        });
        var helix = mock(HelixClient.class);
        var unauthorized = mock(UnauthorizedException.class);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized);
        var chat = mock(TwitchChat.class);
        var builder = mock(TwitchChatBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chat);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.isCredentialValid(any())).thenReturn(Optional.of(true));
        }); var builders = mockStatic(TwitchChatBuilder.class);
             var clients = mockConstruction(TwitchClients.class, (client, context) -> {
                 when(client.getHelix()).thenReturn(helix);

             })) {
            builders.when(TwitchChatBuilder::builder).thenReturn(builder);
            Twitch twitch;
            try (var loader = new TwitchLoader(backing)) { twitch = loader.load().orElseThrow(); }
            robot.interact(() -> { helper.setTwitch(twitch); model.publish(new LoginEvent()); });

            Assertions.assertThatThrownBy(() -> twitch.getTwitchAPI().getClips(List.of("clip")))
                    .isInstanceOf(InvalidCredentialException.class);

            robot.interact(() -> {
                assertThat(helper.isAuthorized()).isFalse();
                assertThat(model.getChannelRepository().getRepository()).isNull();
            });
            while (!queue.isEmpty()) queue.remove().run();
            assertThat(twitch.isClosed()).isTrue();
            assertThat(saved.get().credentials()).isEmpty();
            assertThat(clients.constructed()).hasSize(1);
            var order = inOrder(domain, chat);
            order.verify(domain).clear();
            order.verify(chat).close();
            verify(clients.constructed().getFirst(), never()).close();
            for (var identity : identities.constructed()) verify(identity, never()).revokeCredential(any());
        } finally {
            robot.interact(() -> { model.close(); helper.prepareShutdown(); });
            helper.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mainBorrowsItsRuntimeSessionAndLogoutDoesNotBlockFxOnDomainCleanup(boolean invalidation, FxRobot robot) throws Exception {
        var worker = mock(TaskWorker.class);
        var queue = new LinkedBlockingQueue<Runnable>();
        when(worker.getExecutor()).thenReturn(queue::add);
        var twitch = mock(Twitch.class);
        var invalidated = new AtomicReference<Runnable>();
        doAnswer(invocation -> { invalidated.set(invocation.getArgument(0)); return null; }).when(twitch).onInvalidated(any());
        var domain = mock(ChannelRepository.class);
        var helper = new AppHelper(worker, new EventPublishers(), client -> domain, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var file = new UserDataFile(directory.resolve("session.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        helper.setUserDataFile(file);
        var model = MainModelFactory.create(helper, mock(ChannelGroupRepository.class), mock(ImageLoader.class), unexpectedMainFailure -> { throw new AssertionError(unexpectedMainFailure); });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("cleanup was not released");
            return null;
        }).when(domain).clear();
        try (var endings = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                helper.setTwitch(twitch);
                model.publish(new LoginEvent());
                assertThat(model.getChannelRepository().getRepository()).isSameAs(domain);
                if (invalidation) invalidated.get().run();
                else model.logout();
            });
            robot.interact(() -> {
                assertThat(model.getChannelRepository().getRepository()).isNull();
                assertThat(helper.isAuthorized()).isFalse();
            });
            // 初期ロードは logout で取り消され、最後のジョブがセッションの終了処理。
            Runnable end = null;
            while (!queue.isEmpty()) end = queue.remove();
            assertThat(end).isNotNull();
            var ending = endings.submit(end);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> assertThat(model.isClosed()).isFalse());
                verify(twitch, never()).logout();
                release.countDown();
                ending.get(5, TimeUnit.SECONDS);
                var order = inOrder(domain, twitch);
                order.verify(domain).clear();
                if (invalidation) {
                    order.verify(twitch).close();
                    verify(twitch, never()).logout();
                } else order.verify(twitch).logout();
            } finally {
                release.countDown();
                ending.get(5, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            robot.interact(() -> { model.close(); helper.prepareShutdown(); });
            helper.close();
        }
    }
}
