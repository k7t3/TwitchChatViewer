package com.github.k7t3.tcv.view.action;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public interface Action extends Runnable, EventHandler<ActionEvent> {

    @Override
    default void handle(ActionEvent event) {
        event.consume();
        run();
    }

}
