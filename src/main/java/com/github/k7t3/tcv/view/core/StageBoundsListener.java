package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.prefs.OS;
import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;

public class StageBoundsListener {

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
        if (OS.isMac() && stage instanceof FloatableStage) {

            // MacOSにおいてFloatableStageが意図せずmaximized判定になるため特例処理
            // 最大化プロパティのリスナがなぜかtrueを検出してしまう。(JavaFX 21.0.2)
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

    public StageBounds getCurrent() {
        if (stage == null) throw new IllegalStateException("stage is null");
        return new StageBounds(x, y, width, height, maximized);
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