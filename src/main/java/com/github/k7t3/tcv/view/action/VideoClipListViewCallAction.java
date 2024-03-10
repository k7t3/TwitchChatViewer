package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.clip.PostedClipRepositoryView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.web.BrowserController;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.geometry.Side;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class VideoClipListViewCallAction extends AbstractKeyAction {

    private static final KeyCombination DEFAULT = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN);

    private final ModalPane modalPane;

    private final BrowserController browserController;

    public VideoClipListViewCallAction(ModalPane modalPane, BrowserController browserController) {
        super(DEFAULT);
        this.modalPane = modalPane;
        this.browserController = browserController;
    }

    @Override
    public void run() {
        var repository = AppHelper.getInstance().getClipRepository();

        var tuple = FluentViewLoader.fxmlView(PostedClipRepositoryView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(repository)
                .load();

        var view = tuple.getView();
        tuple.getCodeBehind().setBrowserController(browserController);

        modalPane.usePredefinedTransitionFactories(Side.BOTTOM);
        modalPane.setPersistent(false);
        modalPane.show(view);
    }
}
