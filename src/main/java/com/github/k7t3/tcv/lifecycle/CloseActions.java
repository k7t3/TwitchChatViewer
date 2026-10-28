package com.github.k7t3.tcv.lifecycle;

/** 所有するリソースの解放をすべて試行し、失敗を失わず呼び出し元へ返す。 */
public final class CloseActions {

    private CloseActions() {
    }

    /**
     * 指定順にすべての処理を実行し、最初の失敗を再送出する。
     * 後続の失敗は最初の例外の suppressed exceptions に保持する。
     *
     * @param actions 同期的な解放処理
     */
    public static void runAll(Runnable... actions) {
        Throwable failure = null;
        for (var action : actions) {
            try {
                action.run();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
