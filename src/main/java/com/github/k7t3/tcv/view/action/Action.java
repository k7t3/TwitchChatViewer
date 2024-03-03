package com.github.k7t3.tcv.view.action;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;

public interface Action extends Runnable, EventHandler<ActionEvent> {

    default void install(Button button) {
        button.setOnAction(this);
    }

    @Override
    default void handle(ActionEvent event) {
        event.consume();
        run();
    }

}
