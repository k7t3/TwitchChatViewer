package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.view.clip.VideoClipListView;
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

    private final MainViewModel mainViewModel;

    private final BrowserController browserController;

    public VideoClipListViewCallAction(ModalPane modalPane, MainViewModel mainViewModel, BrowserController browserController) {
        super(DEFAULT);
        this.modalPane = modalPane;
        this.mainViewModel = mainViewModel;
        this.browserController = browserController;
    }

    @Override
    public void run() {
        var loader = FluentViewLoader.fxmlView(VideoClipListView.class);
        loader.resourceBundle(Resources.getResourceBundle());

        var tuple = loader.load();
        var view = tuple.getView();
        var viewModel = tuple.getViewModel();
        tuple.getCodeBehind().setBrowserController(browserController);

        viewModel.installMainViewModel(mainViewModel);

        modalPane.usePredefinedTransitionFactories(Side.BOTTOM);
        modalPane.setPersistent(false);
        modalPane.show(view);
    }
}
