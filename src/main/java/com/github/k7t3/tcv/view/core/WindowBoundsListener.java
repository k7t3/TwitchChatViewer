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

import com.github.k7t3.tcv.app.core.OS;
import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;

public class WindowBoundsListener {

    private boolean maximized = false;

    private double x = -1;
    private double y = -1;
    private double width = -1;
    private double height = -1;

    private double oldX = -1;
    private double oldY = -1;

    private final ChangeListener<Number> xListener = (ob, o, n) -> {
        if (!maximized) {
            x = n.doubleValue();
            oldX = o.doubleValue();
        }
    };

    private final ChangeListener<Number> yListener = (ob, o, n) -> {
        if (!maximized) {
            y = n.doubleValue();
            oldY = o.doubleValue();
        }
    };

    private final ChangeListener<Number> widthListener = (ob, o, n) -> {
        if (!maximized) {
            width = n.doubleValue();
        }
    };

    private final ChangeListener<Number> heightListener = (ob, o, n) -> {
        if (!maximized) {
            height = n.doubleValue();
        }
    };

    private final ChangeListener<Boolean> maximizeListener = (ob, o, n) -> {
        maximized = n;

        if (maximized) {
            x = oldX;
            y = oldY;
        }
    };

    private Stage stage = null;

    public void install(Stage stage) {
        if (this.stage != null) throw new IllegalStateException("already installed");
        this.stage = stage;
        stage.xProperty().addListener(xListener);
        stage.yProperty().addListener(yListener);
        stage.widthProperty().addListener(widthListener);
        stage.heightProperty().addListener(heightListener);
        setMaximized();
        x = stage.getX();
        y = stage.getY();
        width = stage.getWidth();
        height = stage.getHeight();
        oldX = x;
        oldY = y;
    }

    private void setMaximized() {
        // OS_Xのときはウインドウの最大化をサポートしない
        if (OS.isMac()) {
            maximized = false;
        } else {
            maximized = stage.isMaximized();
            stage.maximizedProperty().addListener(maximizeListener);
        }
    }

    public void uninstall() {
        if (stage == null) return;
        stage.xProperty().removeListener(xListener);
        stage.yProperty().removeListener(yListener);
        stage.widthProperty().removeListener(widthListener);
        stage.heightProperty().removeListener(heightListener);
        stage.maximizedProperty().removeListener(maximizeListener);
        stage = null;
        maximized = false;
        x = -1;
        y = -1;
        width = -1;
        height = -1;
        oldX = -1;
        oldY = -1;
    }

    public WindowBounds getCurrent() {
        if (stage == null) throw new IllegalStateException("stage is null");
        return new WindowBounds(x, y, width, height, maximized);
    }

    @Override
    public String toString() {
        return "StageBoundsListener{" +
                "maximized=" + maximized +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", oldX=" + oldX +
                ", oldY=" + oldY +
                ", stage=" + stage +
                '}';
    }
}