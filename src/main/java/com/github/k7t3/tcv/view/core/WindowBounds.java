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

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record WindowBounds(double x, double y, double width, double height, boolean maximized) {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowBounds.class);

    public WindowBounds {
        if (width < 0) throw new IllegalArgumentException("width");
        if (height < 0) throw new IllegalArgumentException("height");
    }

    public void apply(Stage stage) {
        var rect = new Rectangle2D(x, y, width, height);

        // いずれのスクリーンの範囲にも当てはまらないときは適用しない
        if (Screen.getScreensForRectangle(rect).isEmpty()) {
            LOGGER.warn("failed to apply window bounds. cause={}", this);
            return;
        }

        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMaximized(maximized);
    }
}

