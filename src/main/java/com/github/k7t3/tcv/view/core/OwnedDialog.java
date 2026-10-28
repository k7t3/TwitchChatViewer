package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.scene.control.Dialog;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** FX スレッドで、生成から解放まで一つの確認を所有する。取消後の結果は返さない。 */
public final class OwnedDialog<R> implements AutoCloseable {
    private final Supplier<? extends Dialog<R>> factory;
    private ViewResources active;
    private boolean closed;
    private boolean showing;
    private long revision;

    public OwnedDialog(Supplier<? extends Dialog<R>> factory) { this.factory = Objects.requireNonNull(factory); }

    public Optional<R> show(Consumer<Dialog<R>> configure) {
        if (closed || showing) return Optional.empty();
        showing = true;
        var token = revision;
        try {
            var dialog = Objects.requireNonNull(factory.get(), "dialog factory returned null");
            var use = new ViewResources();
            use.add(dialog::close);
            use.add(() -> dialog.setResult(null));
            Optional<R> result;
            try {
                if (!isCurrent(token)) result = Optional.empty();
                else {
                    active = use;
                    configure.accept(dialog);
                    result = isCurrent(token) ? dialog.showAndWait() : Optional.empty();
                }
            } catch (RuntimeException | Error failure) {
                if (active == use) active = null;
                CloseActions.runAll(() -> {
                    if (failure instanceof RuntimeException exception) throw exception;
                    throw (Error) failure;
                }, use::close);
                throw failure;
            }
            if (active == use) active = null;
            use.close();
            return isCurrent(token) ? result : Optional.empty();
        } finally { showing = false; }
    }

    private boolean isCurrent(long token) { return !closed && token == revision; }

    /** 文脈が変わった確認を取り消す。次の独立した操作では再び表示できる。 */
    public void cancel() {
        revision++;
        var previous = active; active = null;
        if (previous != null) previous.close();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        cancel();
    }
}
