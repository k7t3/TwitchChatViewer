package com.github.k7t3.tcv.view.web;

import com.github.k7t3.tcv.app.web.BrowserViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;

public class BrowserController {

    private Parent view;

    private BrowserViewModel viewModel;

    private final SplitPane container;

    public BrowserController(SplitPane container) {
        this.container = container;
    }

    private void loadView() {
        var tuple = FluentViewLoader.fxmlView(BrowserView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        viewModel = tuple.getViewModel();
        view = tuple.getView();

        tuple.getCodeBehind().getCloseButton().setOnAction(e -> {
            container.getItems().remove(view);
            viewModel.clear();
        });
    }

    private BrowserViewModel getViewModel() {
        if (view == null) {
            loadView();
        }
        return viewModel;
    }

    public void load(String uri) {
        var viewModel = getViewModel();
        viewModel.setUrl(uri);
        viewModel.load();
    }

    public void show() {
        if (container.getItems().contains(view)) {
            return;
        }

        container.getItems().add(view);
    }

}
