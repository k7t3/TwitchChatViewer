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

