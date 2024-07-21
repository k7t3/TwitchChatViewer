/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
