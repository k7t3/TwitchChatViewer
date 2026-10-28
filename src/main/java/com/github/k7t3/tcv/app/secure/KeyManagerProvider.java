package com.github.k7t3.tcv.app.secure;

import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 初期化に成功したサービスだけを所有する。初期化失敗は次の呼び出しで再試行できる。 */
final class KeyManagerProvider implements AutoCloseable {
    private final Supplier<KeyManager> create;
    private final ReentrantLock ownership = new ReentrantLock();
    private KeyManager manager;
    private boolean closed;

    KeyManagerProvider(Supplier<KeyManager> create) { this.create = Objects.requireNonNull(create); }

    KeyManager get() {
        try { ownership.lockInterruptibly(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            var failure = new InterruptedIOException("interrupted while waiting for key initialization");
            failure.initCause(interrupted);
            throw new KeyManagerException("failed to access key service", failure);
        }
        try {
            if (closed) throw new IllegalStateException("key service is closed");
            if (manager == null) manager = Objects.requireNonNull(create.get());
            return manager;
        } finally { ownership.unlock(); }
    }

    @Override
    public void close() {
        ownership.lock();
        try {
            if (closed) return;
            closed = true;
            if (manager != null) manager.close();
        } finally { ownership.unlock(); }
    }
}
