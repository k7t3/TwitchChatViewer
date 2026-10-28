package com.github.k7t3.tcv.lifecycle;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Future;

/** 未使用のschedulerを所有し、外部からの終了では実終了を待つ。 */
public final class OwnedScheduler implements AutoCloseable {
    private final ScheduledThreadPoolExecutor scheduler;
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();

    /** @param scheduler workerがまだ開始されていない、所有権を移譲するscheduler */
    public OwnedScheduler(ScheduledThreadPoolExecutor scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
        var factory = scheduler.getThreadFactory();
        scheduler.setThreadFactory(task -> factory.newThread(() -> {
            var worker = Thread.currentThread();
            workers.add(worker);
            try { task.run(); }
            finally { workers.remove(worker); }
        }));
    }

    /** 現在のスレッドが、このscheduler自身のworkerかを返す。 */
    public boolean isWorkerThread() { return workers.contains(Thread.currentThread()); }

    /** 未開始のFutureを取り消す。自分自身は停止要求だけを行い、外部からは実終了を待つ。 */
    @Override public void close() {
        var cleanup = new ArrayList<Runnable>();
        for (var task : scheduler.shutdownNow()) {
            if (task instanceof Future<?> future) cleanup.add(() -> future.cancel(false));
        }
        if (!isWorkerThread()) cleanup.add(scheduler::close);
        CloseActions.runAll(cleanup.toArray(Runnable[]::new));
    }
}
