package com.github.k7t3.tcv.view.core;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

public record StageBounds(double x, double y, double width, double height, boolean maximized) {

    public StageBounds {
        if (width < 0) throw new IllegalArgumentException("width");
        if (height < 0) throw new IllegalArgumentException("height");
    }

    public void apply(Stage stage) {
        var rect = new Rectangle2D(x, y, width, height);

        // いずれのスクリーンの範囲にも当てはまらないときは適用しない
        if (Screen.getScreensForRectangle(rect).isEmpty()) {
            return;
        }

        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMaximized(maximized);
    }

    @Override
    public String toString() {
        return "WindowStatus{" +
                "maximized=" + maximized +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}

