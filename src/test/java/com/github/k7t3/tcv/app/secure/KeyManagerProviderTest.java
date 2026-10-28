package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KeyManagerProviderTest {
    @Test
    void aFailedInitializationCanBeRetriedWithoutPoisoningTheService() {
        var created = new AtomicInteger();
        var manager = mock(KeyManager.class);
        var failure = new KeyManagerException("temporarily unavailable");
        try (var provider = new KeyManagerProvider(() -> {
            if (created.incrementAndGet() == 1) throw failure;
            return manager;
        })) {
            assertThatThrownBy(provider::get).isSameAs(failure);
            assertThat(provider.get()).isSameAs(manager);
            assertThat(provider.get()).isSameAs(manager);
            assertThat(created).hasValue(2);
        }
        verify(manager).close();
    }

    @Test
    void closingAnUnusedServiceDoesNotCreateAKeyStoreAndRejectsLaterInitialization() {
        var created = new AtomicInteger();
        var provider = new KeyManagerProvider(() -> { created.incrementAndGet(); return mock(KeyManager.class); });
        provider.close();
        provider.close();
        assertThat(created).hasValue(0);
        assertThatThrownBy(provider::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentBorrowersUseOneOwnedManager() throws Exception {
        var manager = mock(KeyManager.class);
        var created = new AtomicInteger();
        try (var provider = new KeyManagerProvider(() -> { created.incrementAndGet(); return manager; });
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<java.util.concurrent.Future<KeyManager>>();
            for (var index = 0; index < 8; index++) results.add(workers.submit(provider::get));
            for (var result : results) assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(manager);
            assertThat(created).hasValue(1);
        }
        verify(manager).close();
    }

    @Test
    void closeWaitsForAcceptedInitializationAndOwnsTheCreatedManager() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var manager = mock(KeyManager.class);
        var provider = new KeyManagerProvider(() -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("initialization was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            return manager;
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var getting = workers.submit(provider::get);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = workers.submit(provider::close);
                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(getting.get(5, TimeUnit.SECONDS)).isSameAs(manager);
                closing.get(5, TimeUnit.SECONDS);
                assertThatThrownBy(provider::get).isInstanceOf(IllegalStateException.class);
                verify(manager).close();
            } finally { release.countDown(); }
        } finally { provider.close(); }
    }
    @Test
    void aBorrowerWaitingForAnotherInitializationCanBeCancelledIndependently() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var created = new AtomicInteger();
        var manager = mock(KeyManager.class);
        try (var provider = new KeyManagerProvider(() -> {
            created.incrementAndGet();entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("initialization not released");
            } catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
            return manager;
        });var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(provider::get);
            Thread borrower = null;
            var result = new java.util.concurrent.CompletableFuture<KeyManager>();
            var retained = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var attempting = new CountDownLatch(1);
                borrower = Thread.startVirtualThread(() -> {
                    try { attempting.countDown();result.complete(provider.get()); }
                    catch (Throwable failure) { result.completeExceptionally(failure); }
                    finally { retained.set(Thread.currentThread().isInterrupted()); }
                });
                assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                borrower.interrupt();

                assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(KeyManagerException.class)
                        .satisfies(failure -> assertThat(failure.getCause()).hasCauseInstanceOf(java.io.InterruptedIOException.class));
            } finally {
                release.countDown();
                if (borrower != null) { borrower.join(java.time.Duration.ofSeconds(5));assertThat(borrower.isAlive()).isFalse(); }
            }
            assertThat(retained).isTrue();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(manager);
            assertThat(provider.get()).isSameAs(manager);
            assertThat(created).hasValue(1);
        }
        verify(manager).close();
    }

}
