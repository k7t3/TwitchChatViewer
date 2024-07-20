package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.view.help.AboutView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class OpenAboutCommand extends BasicCommand {

    @Override
    public void execute() {
        if (isNotExecutable()) return;

        executable.set(false);

        var tuple = FluentViewLoader.fxmlView(AboutView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var scene = new Scene(view);
        var stage = new Stage();
        stage.getIcons().addAll(Resources.getIcons());
        stage.setScene(scene);
        stage.setTitle(Resources.getString("about.application"));
        stage.setResizable(false);

        var helper = AppHelper.getInstance();
        var mainWindow = helper.getPrimaryStage();
        stage.initOwner(mainWindow);
        stage.initModality(Modality.WINDOW_MODAL);

        codeBehind.getOkButton().setOnAction(e -> stage.close());

        stage.setOnHidden(e -> executable.set(true));
        stage.showAndWait();
    }

}
