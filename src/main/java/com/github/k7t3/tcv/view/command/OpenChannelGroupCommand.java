package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.command.BasicCommand;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.view.group.ChannelGroupListView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;

public class OpenChannelGroupCommand extends BasicCommand {

    private final ModalPane modalPane;
    private final ChannelGroupRepository groupRepository;

    public OpenChannelGroupCommand(
            ModalPane modalPane,
            ChannelGroupRepository groupRepository,
            ObservableValue<Boolean> condition
    ) {
        this.modalPane = modalPane;
        this.groupRepository = groupRepository;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(new ChannelGroupListViewModel(groupRepository))
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
