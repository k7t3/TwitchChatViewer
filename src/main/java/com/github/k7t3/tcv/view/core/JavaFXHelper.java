package com.github.k7t3.tcv.view.core;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
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

    private static final String INNER_DIALOG_STYLE_CLASS = "inner-dialog-view";

    public static void initAsInnerDialogView(Pane root, double widthRate, double heightRate) {
        if ((widthRate < 0 || 1 < widthRate) && (heightRate < 0 || 1 < heightRate)) {
            throw new IllegalArgumentException();
        }

        // 内部ダイアログのスタイルクラスを追加
        root.getStyleClass().add(INNER_DIALOG_STYLE_CLASS);

        // 幅
        if (0 <= widthRate) {
            root.sceneProperty()
                    .when(root.sceneProperty().isNotNull())
                    .map(Scene::widthProperty)
                    .subscribe(p -> {
                        if (p != null) {
                            root.maxWidthProperty().bind(p.multiply(widthRate));
                        } else {
                            root.maxWidthProperty().unbind();
                        }
                    });
        }

        // 高さ
        if (0 <= heightRate) {
            root.sceneProperty()
                    .when(root.sceneProperty().isNotNull())
                    .map(Scene::heightProperty)
                    .subscribe(p -> {
                        if (p != null) {
                            root.maxHeightProperty().bind(p.multiply(heightRate));
                        } else {
                            root.maxHeightProperty().unbind();
                        }
                    });
        }
    }

}
