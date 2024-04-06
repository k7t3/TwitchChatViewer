package com.github.k7t3.tcv.view.core;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public class JavaFXHelper {

    public static void registerPseudoClass(Node node, String pseudoClassName, ObservableValue<Boolean> activeState) {
        activeState.addListener((ob, o, n) -> updatePseudoClass(node, pseudoClassName, n));
    }

    public static void updatePseudoClass(Node node, String pseudoClass, boolean active) {
        node.pseudoClassStateChanged(PseudoClass.getPseudoClass(pseudoClass), active);
    }

    public static void shownOnce(Window window, EventHandler<WindowEvent> handler) {
        var once = new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                handler.handle(event);
                window.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
            }
        };
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, once);
    }

    public static void closed(Window window, Runnable onClosed) {
        var listener = new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> ob, Boolean o, Boolean n) {
                if (o && !n) {
                    onClosed.run();
                }
            }
        };
        window.showingProperty().addListener(listener);
    }

}
