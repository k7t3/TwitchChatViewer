package com.github.k7t3.tcv.view.core;

import atlantafx.base.controls.ModalPane;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

public class RootPane extends AnchorPane {

    private final ModalPane modalPane;

    private final StackPane contentContainer;

    public RootPane() {
        modalPane = new ModalPane();
        expand(modalPane);
        contentContainer = new StackPane();
        expand(contentContainer);

        getChildren().addAll(modalPane, contentContainer);
    }

    private void expand(Node node) {
        AnchorPane.setTopAnchor(node, 0d);
        AnchorPane.setRightAnchor(node, 0d);
        AnchorPane.setBottomAnchor(node, 0d);
        AnchorPane.setLeftAnchor(node, 0d);
    }

    public ModalPane getModalPane() {
        return modalPane;
    }

    public StackPane getContentContainer() {
        return contentContainer;
    }
}
