package com.github.k7t3.tcv.view.core;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

public class FloatableStage extends Stage {

    private static final double DEFAULT_HEIGHT = 640;
    private static final double DEFAULT_WIDTH = 360;

    private static final double EXPANDABLE_BLOCK_SIZE = 12;

    private final DoubleProperty expandableBlockSize = new SimpleDoubleProperty(EXPANDABLE_BLOCK_SIZE);
    private final BooleanProperty expandable = new SimpleBooleanProperty(true);

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>();

    private final DoubleProperty backgroundOpacity = new SimpleDoubleProperty(1d) {
        @Override
        public void set(double newValue) {
            super.set(Math.clamp(newValue, 0.1, 1d));
        }
    };

    public FloatableStage(Node content) {
        this();
        setContent(content);
    }

    public FloatableStage() {
        initStyle(StageStyle.TRANSPARENT);

        var scene = new FloatableScene();
        setScene(scene);
        scene.backgroundLayer.opacityProperty().bind(backgroundOpacity);

        content.addListener((ob, o, n) -> {
            if (o != null)
                scene.contentLayer.getChildren().remove(o);
            if (n != null)
                scene.contentLayer.getChildren().setAll(n);
        });
    }

    /**
     * ユーザー操作で閉じる場合に使用するメソッド。
     * 内部的に{@link javafx.stage.WindowEvent#WINDOW_CLOSE_REQUEST}を発火させる。
     */
    public void requestClose() {
        fireEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public ObjectProperty<Node> contentProperty() { return content; }
    public Node getContent() { return content.get(); }
    public void setContent(Node content) { this.content.set(content); }

    public DoubleProperty backgroundOpacityProperty() { return backgroundOpacity; }
    public double getBackgroundOpacity() { return backgroundOpacity.get(); }
    public void setBackgroundOpacity(double backgroundOpacity) { this.backgroundOpacity.set(backgroundOpacity); }

    public BooleanProperty expandableProperty() { return expandable; }
    public boolean isExpandable() { return expandable.get(); }
    public void setExpandable(boolean expandable) { this.expandable.set(expandable); }

    private class FloatableScene extends Scene {

        private final Pane backgroundLayer = new Pane();

        private final StackPane contentLayer = new StackPane();

        private double offsetX;
        private double offsetY;

        private final Rectangle expandableTopEdge;
        private final Rectangle expandableRightEdge;
        private final Rectangle expandableBottomEdge;
        private final Rectangle expandableLeftEdge;
        private final Rectangle expandableTopLeftNode;
        private final Rectangle expandableTopRightNode;
        private final Rectangle expandableBottomRightNode;
        private final Rectangle expandableBottomLeftNode;

        public FloatableScene() {
            super(new Pane(), DEFAULT_WIDTH, DEFAULT_HEIGHT, Color.TRANSPARENT);
            var root = (Pane) getRoot();

            var transparentBackground = new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY));
            root.setBackground(transparentBackground);

            backgroundLayer.setStyle("""
                    -fx-background-color: -color-bg-default;
                    -fx-background-radius: 8px;
                    -fx-border-color: -color-border-default;
                    -fx-border-width: 2px;
                    -fx-border-radius: 8px;
                    """);
            backgroundLayer.prefWidthProperty().bind(root.widthProperty());
            backgroundLayer.prefHeightProperty().bind(root.heightProperty());

            contentLayer.translateXProperty().bind(expandableBlockSize);
            contentLayer.translateYProperty().bind(expandableBlockSize);
            contentLayer.prefWidthProperty().bind(root.widthProperty().subtract(expandableBlockSize.multiply(2)));
            contentLayer.prefHeightProperty().bind(root.heightProperty().subtract(expandableBlockSize.multiply(2)));

            root.getChildren().addAll(backgroundLayer, contentLayer);

            addEventHandler(MouseEvent.MOUSE_PRESSED, this::scenePressed);
            addEventHandler(MouseEvent.MOUSE_DRAGGED, this::sceneDragOver);

            // ウインドウ伸長用コントロール
            expandableTopEdge = new Rectangle();
            expandableTopEdge.cursorProperty().bind(expandable.map(x -> x ? Cursor.N_RESIZE : null));
            expandableTopEdge.setFill(Color.TRANSPARENT);
            expandableTopEdge.translateXProperty().bind(expandableBlockSize);
            expandableTopEdge.setTranslateY(0);
            expandableTopEdge.widthProperty().bind(root.widthProperty().subtract(expandableBlockSize.multiply(2)));
            expandableTopEdge.heightProperty().bind(expandableBlockSize);

            expandableRightEdge = new Rectangle();
            expandableRightEdge.cursorProperty().bind(expandable.map(x -> x ? Cursor.E_RESIZE : null));
            expandableRightEdge.setFill(Color.TRANSPARENT);
            expandableRightEdge.translateXProperty().bind(root.widthProperty().subtract(expandableBlockSize));
            expandableRightEdge.translateYProperty().bind(expandableBlockSize);
            expandableRightEdge.widthProperty().bind(expandableBlockSize);
            expandableRightEdge.heightProperty().bind(root.heightProperty().subtract(expandableBlockSize.multiply(2)));

            expandableBottomEdge = new Rectangle();
            expandableBottomEdge.cursorProperty().bind(expandable.map(x -> x ? Cursor.S_RESIZE : null));
            expandableBottomEdge.setFill(Color.TRANSPARENT);
            expandableBottomEdge.translateXProperty().bind(expandableBlockSize);
            expandableBottomEdge.translateYProperty().bind(root.heightProperty().subtract(expandableBlockSize));
            expandableBottomEdge.widthProperty().bind(root.widthProperty().subtract(expandableBlockSize.multiply(2)));
            expandableBottomEdge.heightProperty().bind(expandableBlockSize);

            expandableLeftEdge = new Rectangle();
            expandableLeftEdge.cursorProperty().bind(expandable.map(x -> x ? Cursor.W_RESIZE : null));
            expandableLeftEdge.setFill(Color.TRANSPARENT);
            expandableLeftEdge.setTranslateX(0);
            expandableLeftEdge.translateYProperty().bind(expandableBlockSize);
            expandableLeftEdge.widthProperty().bind(expandableBlockSize);
            expandableLeftEdge.heightProperty().bind(root.heightProperty().subtract(expandableBlockSize.multiply(2)));

            expandableTopLeftNode = new Rectangle();
            expandableTopLeftNode.cursorProperty().bind(expandable.map(x -> x ? Cursor.NW_RESIZE : null));
            expandableTopLeftNode.setFill(Color.TRANSPARENT);
            expandableTopLeftNode.setTranslateX(0);
            expandableTopLeftNode.setTranslateY(0);
            expandableTopLeftNode.widthProperty().bind(expandableBlockSize);
            expandableTopLeftNode.heightProperty().bind(expandableBlockSize);

            expandableTopRightNode = new Rectangle();
            expandableTopRightNode.cursorProperty().bind(expandable.map(x -> x ? Cursor.NE_RESIZE : null));
            expandableTopRightNode.setFill(Color.TRANSPARENT);
            expandableTopRightNode.translateXProperty().bind(root.widthProperty().subtract(expandableBlockSize));
            expandableTopRightNode.setTranslateY(0);
            expandableTopRightNode.widthProperty().bind(expandableBlockSize);
            expandableTopRightNode.heightProperty().bind(expandableBlockSize);

            expandableBottomRightNode = new Rectangle();
            expandableBottomRightNode.cursorProperty().bind(expandable.map(x -> x ? Cursor.SE_RESIZE : null));
            expandableBottomRightNode.setFill(Color.TRANSPARENT);
            expandableBottomRightNode.translateXProperty().bind(root.widthProperty().subtract(expandableBlockSize));
            expandableBottomRightNode.translateYProperty().bind(root.heightProperty().subtract(expandableBlockSize));
            expandableBottomRightNode.widthProperty().bind(expandableBlockSize);
            expandableBottomRightNode.heightProperty().bind(expandableBlockSize);

            expandableBottomLeftNode = new Rectangle();
            expandableBottomLeftNode.cursorProperty().bind(expandable.map(x -> x ? Cursor.SW_RESIZE : null));
            expandableBottomLeftNode.setFill(Color.TRANSPARENT);
            expandableBottomLeftNode.setTranslateX(0);
            expandableBottomLeftNode.translateYProperty().bind(root.heightProperty().subtract(expandableBlockSize));
            expandableBottomLeftNode.widthProperty().bind(expandableBlockSize);
            expandableBottomLeftNode.heightProperty().bind(expandableBlockSize);

            root.getChildren().addAll(
                    expandableTopEdge,
                    expandableRightEdge,
                    expandableBottomEdge,
                    expandableLeftEdge,
                    expandableTopLeftNode,
                    expandableTopRightNode,
                    expandableBottomRightNode,
                    expandableBottomLeftNode
            );

            installExpandEvent();
        }

        private void installExpandEvent() {
            expandableTopEdge.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(-1, e.getScreenY(), -1, -1);
                e.consume();
            });
            expandableRightEdge.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(-1, -1, e.getScreenX(), -1);
                e.consume();
            });
            expandableBottomEdge.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(-1, -1, -1, e.getScreenY());
                e.consume();
            });
            expandableLeftEdge.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(e.getScreenX(), -1, -1, -1);
                e.consume();
            });
            expandableTopLeftNode.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(e.getScreenX(), e.getScreenY(), -1, -1);
                e.consume();
            });
            expandableTopRightNode.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(-1, e.getScreenY(), e.getScreenX(), -1);
                e.consume();
            });
            expandableBottomRightNode.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(-1, -1, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            expandableBottomLeftNode.setOnMouseDragged(e -> {
                if (!expandable.get()) return;
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                stretch(e.getScreenX(), -1, -1, e.getScreenY());
                e.consume();
            });
        }

        /**
         * それぞれの入力パラメータが負数でない場合、指定方向の値を更新する。
         * @param minX 最小X
         * @param minY 最小Y
         * @param maxX 最大X
         * @param maxY 最大Y
         */
        private void stretch(double minX, double minY, double maxX, double maxY) {
            var minLimit = expandableBlockSize.get() * 2;
            var startX = getWindow().getX();
            var startY = getWindow().getY();
            var endX = startX + getWindow().getWidth();
            var endY = startY + getWindow().getHeight();

            if (0d <= minX) {
                if (0 + minLimit <= minX) {
                    var width = endX - minX;
                    getWindow().setX(minX);
                    getWindow().setWidth(width);
                }
            }
            if (0d <= minY) {
                if (0 + minLimit <= minY) {
                    var height = endY - minY;
                    getWindow().setY(minY);
                    getWindow().setHeight(height);
                }
            }
            if (0d <= maxX) {
                var width = maxX - startX;
                getWindow().setWidth(width);
            }
            if (0d <= maxY) {
                var height = maxY - startY;
                getWindow().setHeight(height);
            }
        }

        private void scenePressed(MouseEvent e) {
            this.offsetX = e.getSceneX();
            this.offsetY = e.getSceneY();
            e.consume();
        }

        private void sceneDragOver(MouseEvent e) {
            setX(e.getScreenX() - offsetX);
            setY(e.getScreenY() - offsetY);
            e.consume();
        }

    }

}
