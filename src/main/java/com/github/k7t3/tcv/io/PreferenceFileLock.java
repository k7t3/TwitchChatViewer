package com.github.k7t3.tcv.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 設定と資格情報で共有する、JVM内の直列化とプロセス間ファイル排他。 */
public final class PreferenceFileLock {
    private static final ReentrantLock PROCESS_GUARD = new ReentrantLock();
    private static final ScopedValue<Set<Path>> HELD_LOCKS = ScopedValue.newInstance();

    private PreferenceFileLock() {}

    /**
     * JVM内の受付、共有ファイル、呼出側のDB/ノードの順で処理する。同じスレッドの再入は同じファイルを再取得しない。
     * @param file 全プロセスが共有するロック。nullはJVM内だけで直列化する
     * @param operation DB/ノードの同期と保存を行う処理。ロック取得前にノードのmonitorを保持しないこと
     * @return 処理の結果
     * @throws UncheckedIOException 取得・解放のI/O失敗、または割込みの場合。割込み状態は保持する
     * @param <T> 処理結果の型
     */
    public static <T> T exclusive(Path file, Supplier<T> operation) {
        try { PROCESS_GUARD.lockInterruptibly(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            var failure = new InterruptedIOException("interrupted while waiting for preference storage");
            failure.initCause(interrupted);
            throw new UncheckedIOException(failure);
        }
        try {
            var path = file == null ? null : file.toAbsolutePath().normalize();
            if (path == null || HELD_LOCKS.isBound() && HELD_LOCKS.get().contains(path)) return operation.get();
            try {
                Files.createDirectories(path.getParent());
                try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var lock = InterruptibleFileLocks.acquire(channel)) {
                    var held = new HashSet<Path>();
                    if (HELD_LOCKS.isBound()) held.addAll(HELD_LOCKS.get());
                    held.add(path);
                    return ScopedValue.where(HELD_LOCKS, Set.copyOf(held)).call(operation::get);
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        } finally { PROCESS_GUARD.unlock(); }
    }
}
