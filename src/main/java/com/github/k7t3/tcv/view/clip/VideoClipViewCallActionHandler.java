package com.github.k7t3.tcv.view.clip;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;

public class VideoClipViewCallActionHandler implements EventHandler<ActionEvent> {

    private final ModalPane modalPane;

    public VideoClipViewCallActionHandler(ModalPane modalPane) {
        this.modalPane = modalPane;
    }

    @Override
    public void handle(ActionEvent e) {

        var loader = FluentViewLoader.fxmlView(VideoClipListView.class);
        loader.resourceBundle(Resources.getResourceBundle());

        var tuple = loader.load();
        modalPane.usePredefinedTransitionFactories(Side.BOTTOM);
        modalPane.setPersistent(false);
        modalPane.show(tuple.getView());
    }

}
