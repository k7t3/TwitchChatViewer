package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class SearchChannelViewCallAction extends AbstractKeyAction {

    private static final KeyCombination DEFAULT = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);

    private final ModalPane modalPane;

    private final ChatContainerViewModel chatContainerViewModel;

    public SearchChannelViewCallAction(
            ModalPane modalPane,
            ChatContainerViewModel chatContainerViewModel
    ) {
        super(DEFAULT);
        this.modalPane = modalPane;
        this.chatContainerViewModel = chatContainerViewModel;
    }

    @Override
    public void run() {
        var loader = FluentViewLoader.fxmlView(SearchChannelView.class);
        var tuple = loader.load();
        var view = tuple.getView();
        var viewModel = tuple.getViewModel();
        viewModel.setChatContainerViewModel(chatContainerViewModel);

        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(false);
        modalPane.show(view);

        // フォーカスを遅延させないとIMEが正常に動作しなくなる
        Platform.runLater(() -> tuple.getCodeBehind().getKeywordField().requestFocus());
    }

}
