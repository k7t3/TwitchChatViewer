package com.github.k7t3.tcv.view.core;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.layout.StackPane;
import javafx.stage.WindowEvent;

import java.util.Objects;

/**
 * 標準的な汎用ポップアップコントロール。
 * <p>
 *     {@link atlantafx.base.controls.Popover}を使用すると(恐らく)プロセスが
 *     正常に終了できないケースが発生しうるためその代替目的で作成。
 *     AtlantaFXのサンプルプログラムでもこの現象が再現する気がする…？
 * </p>
 * <p>
 *     {@link #contentProperty()}に割り当てたコンテンツを表示することができる。
 * </p>
 */
public class BasicPopup extends PopupControl {

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>();
    private final DoubleProperty transparency = new SimpleDoubleProperty();

    public BasicPopup() {
    }

    public BasicPopup(Node content) {
        setContent(content);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BasicPopupSkin(this);
    }

    /**
     * このポップアップに表示するノードのプロパティ
     * @return 表示するノード
     */
    public ObjectProperty<Node> contentProperty() { return content; }
    public Node getContent() { return content.get(); }
    public void setContent(Node node) { this.content.set(node); }

    public DoubleProperty transparencyProperty() { return transparency; }
    public double getTransparency() { return transparency.get(); }
    public void setTransparency(double transparency) { this.transparency.set(transparency); }


    private static class BasicPopupSkin implements Skin<BasicPopup> {
        private static final String BACKGROUND_CLASS = "popup-background";

        private final BasicPopup control;
        private final StackPane layout = new StackPane();

        private boolean disposed = false;

        public BasicPopupSkin(BasicPopup control) {
            this.control = control;

            var backgroundLayer = new StackPane();
            backgroundLayer.getStyleClass().add(BACKGROUND_CLASS);
            backgroundLayer.opacityProperty().bind(control.transparency);
            var contentLayer = new StackPane();
            contentLayer.setPadding(new Insets(10));
            layout.getChildren().addAll(backgroundLayer, contentLayer);
            layout.getStylesheets().add(Objects.requireNonNull(getClass().getResource("BasicPopup.css")).toExternalForm());

            if (control.getContent() != null) {
                contentLayer.getChildren().setAll(control.getContent());
            }
            control.content.addListener((ob, o, n) -> contentLayer.getChildren().setAll(n));

            // 初回表示時のみ位置を補正するイベントハンドラ
            var once = new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent windowEvent) {
                    control.setX(control.getX() - control.getWidth() / 2);
                    control.setOnShown(null);
                    control.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                }
            };
            control.addEventHandler(WindowEvent.WINDOW_SHOWN, once);
        }

        @Override
        public BasicPopup getSkinnable() {
            return disposed ? null : control;
        }

        @Override
        public Node getNode() {
            return disposed ? null : layout;
        }

        @Override
        public void dispose() {
            if (disposed) return;
            disposed = true;
            layout.getChildren().clear();
        }
    }
}
