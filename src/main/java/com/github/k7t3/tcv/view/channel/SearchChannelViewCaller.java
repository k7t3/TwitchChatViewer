package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;

public class SearchChannelViewCaller implements EventHandler<ActionEvent> {

    private final ModalPane modalPane;

    private final ChatContainerViewModel chatContainerViewModel;

    public SearchChannelViewCaller(
            ModalPane modalPane,
            ChatContainerViewModel chatContainerViewModel
    ) {
        this.modalPane = modalPane;
        this.chatContainerViewModel = chatContainerViewModel;
    }

    @Override
    public void handle(ActionEvent event) {

        var loader = FluentViewLoader.fxmlView(SearchChannelView.class);
        var tuple = loader.load();
        var view = tuple.getView();
        var viewModel = tuple.getViewModel();
        viewModel.setChatContainerViewModel(chatContainerViewModel);

        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(false);
        modalPane.show(view);

        tuple.getCodeBehind().getKeywordField().requestFocus();
    }
}
