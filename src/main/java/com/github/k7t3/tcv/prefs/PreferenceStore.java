package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.service.SerialIoQueue;
import com.github.k7t3.tcv.io.PreferenceFileLock;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** 所有ノードへの変更を受付順に永続化する。借用ノードは呼出元で直ちに変更する。 */
final class PreferenceStore {
    private static final ScopedValue<PreferenceStore> ACTIVE = ScopedValue.newInstance();
    private final Preferences node;
    private final Path file;
    private final SerialIoQueue queue;
    private final Object admission = new Object();
    private final List<Throwable> failures = new ArrayList<>();
    private final Map<String, Object> pending = new HashMap<>();
    private final Map<String, Optional<String>> cached = new HashMap<>();
    private CompletableFuture<Void> closed;

    PreferenceStore(Preferences node, Path file) {
        this(node, file, task -> Thread.ofVirtual().name("TCV-Preferences").start(task));
    }

    PreferenceStore(Preferences node, Path file, Executor executor) {
        this.node = java.util.Objects.requireNonNull(node);
        this.file = file;
        queue = file == null ? null : new SerialIoQueue(executor);
    }

    Optional<String> read(String key) {
        synchronized (admission) {
            if (cached.containsKey(key)) return cached.get(key);
        }
        return Optional.ofNullable(node.get(key, null));
    }

    void load(Set<String> keys) {
        if (queue == null) return;
        exclusive(() -> {
            var loaded = new HashMap<String, Optional<String>>();
            synchronized (node) {
                sync();
                keys.forEach(key -> loaded.put(key, Optional.ofNullable(node.get(key, null))));
            }
            synchronized (admission) {
                loaded.forEach((key, value) -> { if (!pending.containsKey(key)) cached.put(key, value); });
            }
            return null;
        });
    }

    void committed(Map<String, Optional<String>> values) {
        if (queue == null) return;
        synchronized (admission) {
            values.forEach((key, value) -> { if (!pending.containsKey(key)) cached.put(key, value); });
        }
    }

    Map<String, Optional<String>> currentProjection(Map<String, Optional<String>> values) {
        if (queue == null) return Map.copyOf(values);
        var current = new HashMap<String, Optional<String>>();
        synchronized (admission) {
            values.forEach((key, value) -> { if (value.equals(cached.get(key))) current.put(key, value); });
        }
        return Map.copyOf(current);
    }

    void write(String key, Optional<String> value, boolean recoveryKey) {
        Runnable change = () -> {
            if (recoveryKey) PreferenceProjection.claim(node, UUID.randomUUID(), Map.of(key, value));
            if (value.isPresent()) node.put(key, value.orElseThrow());
            else node.remove(key);
        };
        if (queue == null) {
            synchronized (admission) { ensureOpen(); }
            if (recoveryKey) { synchronized (node) { change.run(); } }
            else change.run();
            return;
        }
        synchronized (admission) {
            ensureOpen();
            var accepted = new Object();
            pending.put(key, accepted);
            cached.put(key, value);
            var started = new AtomicBoolean();
            queue.submit(() -> {
                started.set(true);
                try {
                    locked(() -> {
                        synchronized (node) {
                            sync();
                            var previous = Optional.ofNullable(node.get(key, null));
                            var owners = recoveryKey ? PreferenceProjection.snapshotOwners(node) : Optional.<String>empty();
                            try { change.run(); flush(); }
                            catch (RuntimeException | Error failure) {
                                // 失敗した通常変更を、古いSQL投影より新しい確定済み変更として残さない。
                                try {
                                    CloseActions.runAll(() -> {
                                        if (previous.isPresent()) node.put(key, previous.orElseThrow());
                                        else node.remove(key);
                                    }, () -> {
                                        if (recoveryKey) PreferenceProjection.restoreOwners(node, owners);
                                    }, this::flush);
                                } catch (RuntimeException | Error restore) {
                                    if (restore != failure) failure.addSuppressed(restore);
                                }
                                throw failure;
                            }
                        }
                        return null;
                    });
                    synchronized (admission) {
                        // 同じキーへの後続変更を、古い書込の完了で消さない。
                        if (pending.get(key) == accepted) pending.remove(key);
                    }
                    return null;
                } catch (RuntimeException | Error failure) {
                    synchronized (admission) { failures.add(failure); }
                    throw failure;
                }
            }).whenComplete((ignored, failure) -> {
                if (failure != null && !started.get()) {
                    var cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    // 本体が記録する失敗と分け、未開始の書込だけを所有する。
                    synchronized (admission) { failures.add(cause); }
                }
            });
        }
    }

    <T> T exclusive(Supplier<T> operation) {
        if (ACTIVE.isBound() && ACTIVE.get() == this) return operation.get();
        return call(operation, false);
    }

    private <T> T call(Supplier<T> operation, boolean finishing) {
        if (queue == null) {
            synchronized (admission) { if (!finishing) ensureOpen(); }
            return PreferenceFileLock.exclusive(null, operation);
        }
        final CompletionStage<T> accepted;
        var started = new AtomicBoolean();
        synchronized (admission) {
            if (!finishing) ensureOpen();
            accepted = queue.submit(() -> {
                started.set(true);
                if (!finishing) { reportFailures(null); return locked(operation); }
                T result = null;
                Throwable finalFailure = null;
                try { result = locked(operation); }
                catch (RuntimeException | Error failure) { finalFailure = failure; }
                // ファイル取得に失敗しても、先行する書込の失敗を失わない。
                reportFailures(finalFailure);
                return result;
            });
        }
        try { return await(accepted); }
        catch (RuntimeException | Error failure) {
            // 終了要求自体の投入拒否でも、先行書込みの失敗を回収する。
            if (finishing && !started.get()) reportFailures(failure);
            throw failure;
        }
    }

    private static <T> T await(CompletionStage<T> accepted) {
        try { return accepted.toCompletableFuture().join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failure.getCause());
        }
    }

    void save() {
        if (queue == null || ACTIVE.isBound() && ACTIVE.get() == this) { flush(); return; }
        call(() -> {
            synchronized (node) { sync(); flush(); }
            return null;
        }, true);
    }

    private void reportFailures(Throwable finalFailure) {
        final List<Throwable> previous;
        synchronized (admission) { previous = List.copyOf(failures); }
        if (!previous.isEmpty()) {
            var failure = new IllegalStateException("preference writes failed", previous.getFirst());
            previous.stream().skip(1).forEach(failure::addSuppressed);
            if (finalFailure != null) failure.addSuppressed(finalFailure);
            throw failure;
        }
        if (finalFailure instanceof RuntimeException failure) throw failure;
        if (finalFailure instanceof Error failure) throw failure;
    }

    void close(Runnable finish) {
        if (ACTIVE.isBound() && ACTIVE.get() == this) throw new IllegalStateException("cannot close active preference storage");
        final boolean owner;
        final CompletableFuture<Void> completion;
        synchronized (admission) {
            owner = closed == null;
            if (owner) closed = new CompletableFuture<>();
            completion = closed;
        }
        if (owner) {
            try { finish.run(); completion.complete(null); }
            catch (RuntimeException | Error failure) { completion.completeExceptionally(failure); }
        }
        await(completion);
    }

    private void ensureOpen() {
        if (closed != null) throw new IllegalStateException("preference storage is closed");
    }

    void checkOpen() { synchronized (admission) { ensureOpen(); } }

    private <T> T locked(Supplier<T> operation) {
        // DBを使う処理では、共有ファイル→DB→ノードの取得順を呼出側に維持させる。
        return PreferenceFileLock.exclusive(file, () -> ScopedValue.where(ACTIVE, this).call(operation::get));
    }

    private void sync() {
        try { node.sync(); }
        catch (BackingStoreException failure) { throw new IllegalStateException(failure); }
    }

    private void flush() {
        synchronized (node) {
            try { node.flush(); }
            catch (BackingStoreException failure) { throw new IllegalStateException(failure); }
        }
    }
}
