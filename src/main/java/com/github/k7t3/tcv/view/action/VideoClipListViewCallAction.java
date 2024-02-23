package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.view.clip.VideoClipListView;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.geometry.Side;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class VideoClipListViewCallAction extends AbstractKeyAction {

    private static final KeyCombination DEFAULT = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN);

    private final ModalPane modalPane;

    public VideoClipListViewCallAction(ModalPane modalPane) {
        super(DEFAULT);
        this.modalPane = modalPane;
    }

    @Override
    public void run() {
        var loader = FluentViewLoader.fxmlView(VideoClipListView.class);
        loader.resourceBundle(Resources.getResourceBundle());

        var tuple = loader.load();
        modalPane.usePredefinedTransitionFactories(Side.BOTTOM);
        modalPane.setPersistent(false);
        modalPane.show(tuple.getView());
    }
}
