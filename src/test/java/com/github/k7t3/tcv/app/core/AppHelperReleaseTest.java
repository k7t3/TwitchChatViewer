package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppHelperReleaseTest {
    @Test
    void shutdownWaitsForRegisteredReleaseAfterDrainingTheWorkerAndRetainsItsFailure() throws Exception {
        var worker = mock(TaskWorker.class);
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var drained = new CountDownLatch(1);
        doAnswer(invocation -> { drained.countDown(); return null; }).when(worker).close();
        var releasing = new CompletableFuture<Void>();
        var failure = new IllegalStateException("authentication cleanup failed");
        helper.awaitOnShutdown(releasing.minimalCompletionStage());
        try (var closing = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = closing.submit(helper::close);
            try {
                assertThat(drained.await(5, TimeUnit.SECONDS)).isTrue();
                verify(publishers, never()).close();
                assertThat(result.isDone()).isFalse();
                releasing.completeExceptionally(failure);
                assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(failure);
                verify(publishers).close();
                assertThatThrownBy(helper::close).isSameAs(failure);
            } finally { releasing.complete(null); }
        }
    }

    @Test
    void aReleaseFailureCompletedBeforeShutdownIsStillReported() {
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(mock(TaskWorker.class), publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var released = new CompletableFuture<Void>();
        var failure = new IllegalStateException("earlier cleanup failed");
        helper.awaitOnShutdown(released.minimalCompletionStage());
        released.completeExceptionally(failure);
        assertThatThrownBy(helper::close).isSameAs(failure);
        verify(publishers).close();
    }
}
