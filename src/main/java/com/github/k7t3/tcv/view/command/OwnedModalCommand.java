package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.util.ArrayDeque;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;

/** FXML 読込前から一時画面を所有し、共有 ModalPane の別コンテンツには作用しない。 */
abstract class OwnedModalCommand extends BasicCommand implements AutoCloseable {
    private final ModalPane pane;
    private final ObservableValue<Boolean> condition;
    private final Side side;
    private ModalSession active;
    private boolean closed;
    private final ChangeListener<Boolean> displayChanged = (observable, old, value) -> { if (!Boolean.TRUE.equals(value)) releaseActive(false); };
    private final ChangeListener<Node> contentChanged = (observable, old, value) -> {
        if (active != null && active.root != null && active.root != value) releaseActive(false);
    };
    private final ChangeListener<Boolean> conditionChanged = (observable, old, value) -> { if (!Boolean.TRUE.equals(value)) releaseActive(true); };

    OwnedModalCommand(ModalPane pane, ObservableValue<Boolean> condition, Side side) {
        this.pane = pane; this.condition = condition; this.side = side;
        executable.bind(condition);
        pane.displayProperty().addListener(displayChanged);
        pane.contentProperty().addListener(contentChanged);
        condition.addListener(conditionChanged);
    }

    protected abstract Parent load(ModalSession session);
    protected boolean mayOpen() { return true; }
    protected boolean persistent() { return false; }

    @Override public final void execute() {
        if (closed) throw new IllegalStateException("modal command is closed");
        if (!Boolean.TRUE.equals(condition.getValue()) || !mayOpen()) return;
        releaseActive(true);
        var opening = new ModalSession(); active = opening;
        try {
            opening.root = load(opening);
            if (active != opening || closed) return;
            pane.setPersistent(persistent());
            pane.usePredefinedTransitionFactories(side);
            pane.show(opening.root);
            Platform.runLater(() -> {
                if (active != opening || closed || !pane.isDisplay() || pane.getContent() != opening.root) return;
                try { opening.afterShown.run(); }
                catch (RuntimeException | Error failure) { failed(opening, failure); }
            });
        } catch (RuntimeException | Error failure) { failed(opening, failure); }
    }

    private void failed(ModalSession opening, Throwable failure) {
        CloseActions.runAll(() -> { if (failure instanceof RuntimeException exception) throw exception; throw (Error) failure; },
                () -> release(opening, true));
    }

    private void releaseActive(boolean hide) { if (active != null) release(active, hide); }

    private void release(ModalSession session, boolean hide) {
        if (active == session) active = null;
        CloseActions.runAll(session::release, () -> {
            if (hide && session.root != null && pane.getContent() == session.root) pane.hide(true);
        });
    }

    @Override public final void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> {
            pane.displayProperty().removeListener(displayChanged);
            pane.contentProperty().removeListener(contentChanged);
            condition.removeListener(conditionChanged);
            executable.unbind(); executable.set(false);
        }, () -> releaseActive(true));
    }

    protected static final class ModalSession {
        private final ArrayDeque<Runnable> releases = new ArrayDeque<>();
        private Parent root;
        private Runnable afterShown = () -> {};
        private boolean released;

        public void own(Runnable close) { if (released) close.run(); else releases.addFirst(close); }
        public boolean isReleased() { return released; }
        public void onShown(Runnable action) { afterShown = action; }
        private void release() {
            if (released) return;
            released = true;
            var actions = releases.toArray(Runnable[]::new); releases.clear();
            afterShown = () -> {};
            CloseActions.runAll(actions);
        }
    }
}
