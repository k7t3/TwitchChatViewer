package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainBrowserErrorOwnerTest {
    @Test
    void selectedChannelsBrowserFailureUsesTheMainWindowsErrorOwner(FxRobot robot) throws Exception {
        try (var fixture = new MainErrorFixture(robot)) {
            var channel = mock(TwitchChannelViewModel.class);
            when(channel.getBroadcaster()).thenReturn(new Broadcaster("123", "channel", "Channel", "", ""));
            when(channel.getStreamTitle()).thenReturn("title");
            var failure = new UnsupportedOperationException("browser unavailable");
            robot.interact(() -> {
                fixture.model.getChannelListViewModel().getSelectedChannels().add(channel);

                fixture.model.getChannelListViewModel().openSelectedChannelPageOnBrowser();
            });

            fixture.failNextBrowserCall(failure);

            fixture.verifyErrorOwner();
        }
    }

    @Test
    void authenticationBrowserFailureUsesTheMainWindowsErrorOwner(FxRobot robot) throws Exception {
        var fixture = new MainErrorFixture(robot);
        var loaders = new AtomicReference<MockedConstruction<TwitchLoader>>();
        var authentication = new AtomicReference<AuthenticatorViewModel>();
        var request = new AtomicReference<FXTask<?>>();
        var failure = new UnsupportedOperationException("browser unavailable");
        try {
            robot.interact(() -> {
                loaders.set(mockConstruction(TwitchLoader.class, (loader, context) -> {
                    when(loader.load()).thenReturn(Optional.empty());
                    when(loader.startAuthenticate(any())).thenReturn(new TwitchLoader.DeviceFlow(
                            "CODE", "https://example.invalid/verify", "https://example.invalid/complete"));
                }));
                authentication.set(fixture.model.createAuthenticator(mock(CredentialStore.class)));
                request.set(authentication.get().loadClientAsync());
                fixture.queue.remove().run();
            });
            request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(authentication.get().isInitialized()).isTrue();
                request.set(authentication.get().startAuthenticateAsync());
                fixture.queue.remove().run();
            });
            request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(authentication.get().getAuthUri()).isEqualTo("https://example.invalid/complete");
                assertThat(fixture.queue).hasSize(1);
                fixture.queue.remove().run(); // QRコードの生成を完了させる。

                authentication.get().openAuthUri();
            });

            fixture.failNextBrowserCall(failure);

            fixture.verifyErrorOwner();
            robot.interact(() -> assertThat(authentication.get().getAuthUri()).isEqualTo("https://example.invalid/complete"));
        } finally {
            try {
                fixture.close();
                if (authentication.get() != null) authentication.get().resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally { robot.interact(() -> { if (loaders.get() != null) loaders.get().close(); }); }
        }
    }

}
