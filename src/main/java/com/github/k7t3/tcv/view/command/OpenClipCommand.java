package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.view.clip.PostedClipRepositoryView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;

public class OpenClipCommand extends BasicCommand {

    private final ModalPane modalPane;

    private final PostedClipRepository clipRepository;

    public OpenClipCommand(
            ModalPane modalPane,
            PostedClipRepository clipRepository,
            ObservableValue<Boolean> condition
    ) {
        this.modalPane = modalPane;
        this.clipRepository = clipRepository;
        executable.bind(condition);
    }

    @Override
    public void execute() {
        var tuple = FluentViewLoader.fxmlView(PostedClipRepositoryView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(clipRepository)
                .load();
        var view = tuple.getView();

        modalPane.usePredefinedTransitionFactories(Side.BOTTOM);
        modalPane.setPersistent(false);
        modalPane.show(view);
    }
}
