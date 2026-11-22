package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** FX スレッドで画面が所有する解除処理を、生成順の逆に一度だけ実行する。 */
public final class ViewResources implements AutoCloseable {
    private final List<Runnable> releases = new ArrayList<>();
    private boolean closed;

    public boolean isClosed() { return closed; }

    /** 構築中に終了へ再入した後で返った資源も直ちに解放する。 */
    public void add(Runnable release) {
        Objects.requireNonNull(release);
        if (closed) release.run(); else releases.add(release);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var owned = releases.reversed().toArray(Runnable[]::new);
        releases.clear();
        CloseActions.runAll(owned);
    }
}
