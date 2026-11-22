package com.github.k7t3.tcv.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** ネイティブのblocking lockに入らず、割込みで終了できるファイル排他。 */
public final class InterruptibleFileLocks {
    private InterruptibleFileLocks() {}

    /**
     * 取得まで待ち、呼出側へロックの解放責任を渡す。channelは借用し、閉じない。
     * 同一JVMでの重複取得は呼出側で直列化する。
     * @param channel 書込可能な開いたchannel
     * @return 呼出側が解放する排他ロック
     * @throws FileLockInterruptionException 割込み時。割込み状態を保持する
     * @throws IOException ファイル排他の取得に失敗した場合
     */
    public static FileLock acquire(FileChannel channel) throws IOException {
        // macOSのblocking lockは、割込みを送った側もnative close待ちに巻き込む。
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new FileLockInterruptionException();
            var lock = channel.tryLock();
            if (lock != null) return lock;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }
}
