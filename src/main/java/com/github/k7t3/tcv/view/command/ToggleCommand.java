package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.service.BasicCommand;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;

public class ToggleCommand extends BasicCommand {

    private final BooleanProperty toggle;

    public ToggleCommand(BooleanProperty toggle, ObservableValue<Boolean> condition) {
        this.toggle = toggle;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        toggle.set(!toggle.get());
    }
}
