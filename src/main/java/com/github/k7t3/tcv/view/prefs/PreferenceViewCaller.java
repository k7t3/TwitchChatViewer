package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.util.Duration;

public class PreferenceViewCaller implements EventHandler<ActionEvent> {

    private final ModalPane modalPane;

    public PreferenceViewCaller(ModalPane modalPane) {
        this.modalPane = modalPane;
    }

    @Override
    public void handle(ActionEvent event) {
        var tuple = FluentViewLoader.fxmlView(PreferencesView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        tuple.getCodeBehind().setModalPane(modalPane);

        modalPane.usePredefinedTransitionFactories(Side.RIGHT);
        modalPane.setInTransitionFactory((node) -> Animations.zoomIn(node, Duration.millis(400)));
        modalPane.setOutTransitionFactory((node) -> Animations.zoomOut(node, Duration.millis(400)));
        modalPane.setPersistent(true);
        modalPane.show(view);
    }

}
