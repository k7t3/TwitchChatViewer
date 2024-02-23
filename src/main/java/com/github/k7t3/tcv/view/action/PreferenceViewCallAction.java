package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.geometry.Side;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.util.Duration;

public class PreferenceViewCallAction extends AbstractKeyAction {

    private static final KeyCombination DEFAULT = new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN);

    private final ModalPane modalPane;

    public PreferenceViewCallAction(ModalPane modalPane) {
        super(DEFAULT);
        this.modalPane = modalPane;
    }

    @Override
    public void run() {
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
