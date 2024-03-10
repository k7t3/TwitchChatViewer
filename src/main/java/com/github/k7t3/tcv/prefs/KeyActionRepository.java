package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.view.action.KeyAction;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeyActionRepository {

    private final List<KeyAction> actions = new ArrayList<>();

    private Scene scene;

    public void install(Scene scene) {
        if (this.scene != null) return;
        this.scene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> actions.stream().filter(a -> a.accept(e)).findFirst().ifPresent(r -> {
            if (r.isDisable()) return;
            r.run();
            e.consume();
        }));
    }

    public void addAction(KeyAction action) {
        this.actions.add(action);
    }

    public List<KeyAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

}
