package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.stage.WindowEvent;
import java.util.function.Consumer;

/** 一つのフローティング窓の復元・座標監視・終了時の保存受付を所有する。 */
public final class WindowBoundsScope implements AutoCloseable {
    private final FloatableStage stage;
    private final WindowBoundsService service;
    private final String identity;
    private final Consumer<Throwable> failures;
    private final WindowBoundsListener bounds = new WindowBoundsListener();
    private final EventHandler<WindowEvent> onShown = event -> shown = true;
    private final EventHandler<WindowEvent> onHiding = event -> close();
    private final EventHandler<WorkerStateEvent> restored = event -> applyRestoredBounds();
    private final EventHandler<WorkerStateEvent> failed = event -> reportFailure();
    private FXTask<WindowBounds> restore;
    private boolean shown;
    private boolean closed;

    public WindowBoundsScope(FloatableStage stage, String identity, WindowBoundsService service) {
        this(stage, identity, service, failure -> ExceptionHandler.handle(stage, failure));
    }

    WindowBoundsScope(FloatableStage stage, String identity, WindowBoundsService service, Consumer<Throwable> failures) {
        this.stage = stage;
        this.identity = identity;
        this.service = service;
        this.failures = failures;
        stage.onClose(this::close);
        if (closed) return;
        try {
            shown = stage.isShowing();
            bounds.install(stage);
            stage.addEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
            stage.addEventHandler(WindowEvent.WINDOW_HIDING, onHiding);
            restore = service.getBoundsAsync(identity);
            if (closed) { restore.cancel(true); return; }
            restore.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, restored);
            restore.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, failed);
            if (restore.getState() == Worker.State.SUCCEEDED) applyRestoredBounds();
            else if (restore.getState() == Worker.State.FAILED) failures.accept(restore.getException());
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, this::release);
        }
    }

    private void reportFailure() {
        if (!closed && restore != null) failures.accept(restore.getException());
    }

    private void applyRestoredBounds() {
        if (!closed) restore.getValue().apply(stage, () -> !closed);
    }

    @Override public void close() {
        if (closed) return;
        var last = shown ? bounds.getCurrent() : null;
        CloseActions.runAll(this::release, () -> { if (last != null) service.saveBoundsAsync(identity, last); });
    }

    private void release() {
        closed = true;
        var owned = restore;
        restore = null;
        CloseActions.runAll(() -> {
            if (owned == null) return;
            owned.removeEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, restored);
            owned.removeEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, failed);
            owned.cancel(true);
        }, () -> {
            stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
            stage.removeEventHandler(WindowEvent.WINDOW_HIDING, onHiding);
        }, bounds::uninstall);
    }
}
