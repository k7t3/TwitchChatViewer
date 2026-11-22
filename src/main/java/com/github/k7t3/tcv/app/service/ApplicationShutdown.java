package com.github.k7t3.tcv.app.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** UI の終了準備、バックグラウンドでの解放、UI の終了通知を順に実行する。 */
public final class ApplicationShutdown {

    private final Executor worker;
    private final Executor ui;
    private final Runnable prepare;
    private final Runnable release;
    private final Runnable exit;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final CompletionStage<Void> result = completion.minimalCompletionStage();
    private final CompletableFuture<Void> released = new CompletableFuture<>();
    private final CompletionStage<Void> releasedResult = released.minimalCompletionStage();
    private boolean requested;

    /**
     * @param worker 解放処理用。解放対象のワーカーとは別の executor を使用すること
     * @param ui UI スレッドへ処理を送る executor
     * @param prepare 呼び出し元 UI スレッドで行う終了準備
     * @param release バックグラウンドで行うリソースの解放
     * @param exit 解放後に UI スレッドで行うアプリケーション終了
     */
    public ApplicationShutdown(Executor worker, Executor ui, Runnable prepare, Runnable release, Runnable exit) {
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
        this.prepare = Objects.requireNonNull(prepare);
        this.release = Objects.requireNonNull(release);
        this.exit = Objects.requireNonNull(exit);
    }

    /**
     * UI スレッドから終了を要求する。同じインスタンスへの要求は一度だけ実行する。
     * 処理または executor への投入に失敗した場合、返す stage は例外で完了する。
     */
    public synchronized CompletionStage<Void> request() {
        if (requested) return result;
        requested = true;
        var preparationFailure = invoke(prepare, null);
        try {
            worker.execute(() -> finishOnUi(invoke(release, preparationFailure)));
        } catch (RuntimeException | Error failure) {
            // 専用スレッドを作成できなくても、所有リソースの解放は省略しない。
            finishOnUi(invoke(release, append(preparationFailure, failure)));
        }
        return result;
    }

    private void finishOnUi(Throwable previous) {
        if (previous == null) released.complete(null);
        else released.completeExceptionally(previous);
        try {
            ui.execute(() -> {
                complete(invoke(exit, previous));
            });
        } catch (RuntimeException | Error failure) {
            complete(append(previous, failure));
        }
    }

    /** UI の終了通知を待たず、リソースの解放処理までの完了を返す。 */
    public CompletionStage<Void> resourcesReleased() {
        return releasedResult;
    }

    private void complete(Throwable failure) {
        if (failure == null) completion.complete(null);
        else completion.completeExceptionally(failure);
    }

    private static Throwable invoke(Runnable action, Throwable previous) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            return append(previous, failure);
        }
        return previous;
    }

    private static Throwable append(Throwable previous, Throwable failure) {
        if (previous == null) return failure;
        if (previous != failure) previous.addSuppressed(failure);
        return previous;
    }
}
