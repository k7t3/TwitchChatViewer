package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.Credential;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

/** 実認証Taskからストレージを読み、Twitchへの通信とDevice Flowだけを差し替える。 */
public final class CredentialReadProbeViewModel extends AuthenticatorViewModel {
    public CredentialReadProbeViewModel(CredentialStore store, Executor executor,
                                        CompletableFuture<List<Credential>> loaded) {
        this(store, executor, loaded, () -> {});
    }

    public CredentialReadProbeViewModel(CredentialStore store, Executor executor,
                                        CompletableFuture<List<Credential>> loaded, Runnable beforeRead) {
        super(() -> {
            var loader = mock(TwitchLoader.class);
            when(loader.load()).thenAnswer(call -> {
                try {
                    beforeRead.run();
                    loaded.complete(List.copyOf(store.loadCredentials()));
                    return Optional.empty();
                } catch (RuntimeException | Error failure) {
                    loaded.completeExceptionally(failure);
                    throw failure;
                }
            });
            return loader;
        }, client -> { throw new AssertionError("unexpected client adoption"); }, executor,
                failure -> loaded.completeExceptionally(failure));
    }

    @Override public void beginAuthentication() { loadClientAsync(); }
}
