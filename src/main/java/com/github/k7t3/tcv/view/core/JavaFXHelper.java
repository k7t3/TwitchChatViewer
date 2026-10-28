/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.core;

import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Subscription;

public class JavaFXHelper {

    public static Subscription registerPseudoClass(Node node, String pseudoClassName, ObservableValue<Boolean> activeState) {
        return activeState.subscribe(active -> updatePseudoClass(node, pseudoClassName, Boolean.TRUE.equals(active)));
    }

    public static void updatePseudoClass(Node node, String pseudoClass, boolean active) {
        node.pseudoClassStateChanged(PseudoClass.getPseudoClass(pseudoClass), active);
    }

    private static final String INNER_DIALOG_STYLE_CLASS = "inner-dialog-view";

    public static void initAsInnerWindowFixed(Pane root, double width, double height) {
        // 内部ダイアログのスタイルクラスを追加
        root.getStyleClass().add(INNER_DIALOG_STYLE_CLASS);

        if (0 <= width) {
            root.setMaxWidth(width);
        }

        if (0 <= height) {
            root.setMaxHeight(height);
        }
    }

    public static javafx.util.Subscription initAsInnerWindow(Pane root, double widthRate, double heightRate) {
        if ((widthRate < 0 || 1 < widthRate) && (heightRate < 0 || 1 < heightRate)) throw new IllegalArgumentException();
        root.getStyleClass().add(INNER_DIALOG_STYLE_CLASS);
        var subscription = root.sceneProperty().subscribe(scene -> {
            if (0 <= widthRate) {
                root.maxWidthProperty().unbind();
                if (scene != null) root.maxWidthProperty().bind(scene.widthProperty().multiply(widthRate));
            }
            if (0 <= heightRate) {
                root.maxHeightProperty().unbind();
                if (scene != null) root.maxHeightProperty().bind(scene.heightProperty().multiply(heightRate));
            }
        });
        return () -> {
            subscription.unsubscribe();
            if (0 <= widthRate) root.maxWidthProperty().unbind();
            if (0 <= heightRate) root.maxHeightProperty().unbind();
        };
    }

    public static Bounds computeScreenBounds(Node node) {
        var local = node.getBoundsInLocal();
        return node.localToScreen(local);
    }

}
