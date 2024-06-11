package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.command.BasicCommand;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.geometry.Side;
import javafx.util.Duration;

public class OpenPreferencesCommand extends BasicCommand {

    private final ModalPane modalPane;

    public OpenPreferencesCommand(ModalPane modalPane) {
        this.modalPane = modalPane;
    }

    @Override
    public void execute() {
        var tuple = FluentViewLoader.fxmlView(PreferencesView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        tuple.getCodeBehind().setModalPane(modalPane);

        modalPane.usePredefinedTransitionFactories(Side.RIGHT);
        modalPane.setInTransitionFactory((node) -> Animations.zoomIn(node, Duration.millis(400)));
        modalPane.setOutTransitionFactory((node) -> Animations.zoomOut(node, Duration.millis(200)));
        modalPane.setPersistent(true);
        modalPane.show(view);
    }
}
