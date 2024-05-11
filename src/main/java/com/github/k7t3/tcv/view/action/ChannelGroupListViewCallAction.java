package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.view.group.ChannelGroupListView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class ChannelGroupListViewCallAction extends AbstractKeyAction {

    private static final KeyCombination DEFAULT = new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN);

    private final ModalPane modalPane;

    public ChannelGroupListViewCallAction(ModalPane modalPane) {
        super(DEFAULT);
        this.modalPane = modalPane;
    }

    @Override
    public void run() {
        var helper = AppHelper.getInstance();

        var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(new ChannelGroupListViewModel(helper.getChannelGroupRepository()))
                .load();
        var view = tuple.getView();
        var behind = tuple.getCodeBehind();

        modalPane.setPersistent(false);
        modalPane.usePredefinedTransitionFactories(Side.LEFT);
        modalPane.show(view);

        // ListViewの表示要素を先頭にする処理
        behind.onOpened();
    }
}
