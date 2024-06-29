package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;

public class OpenSearchChannelCommand extends BasicCommand {

    private final ModalPane modalPane;
    private final ChannelViewModelRepository repository;

    public OpenSearchChannelCommand(
            ModalPane modalPane,
            ChannelViewModelRepository repository,
            ObservableValue<Boolean> condition
    ) {
        this.modalPane = modalPane;
        this.repository = repository;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        var helper = AppHelper.getInstance();
        var viewModel = new SearchChannelViewModel(repository);
        viewModel.twitchProperty().bind(helper.twitchProperty());

        var tuple = FluentViewLoader
                .fxmlView(SearchChannelView.class)
                .viewModel(viewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        var view = tuple.getView();

        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(false);
        modalPane.show(view);

        // フォーカスを遅延させないとIMEが正常に動作しなくなる
        Platform.runLater(() -> tuple.getCodeBehind().getKeywordField().requestFocus());
    }
}
