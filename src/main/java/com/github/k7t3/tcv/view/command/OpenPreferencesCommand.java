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

import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class OpenPreferencesCommand extends BasicCommand {

    public OpenPreferencesCommand() {
    }

    @Override
    public void execute() {
        if (isNotExecutable()) return;

        var helper = AppHelper.getInstance();
        var stage = new Stage();
        stage.initOwner(helper.getPrimaryStage());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.getIcons().addAll(Resources.getIcons());
        stage.setTitle(Resources.getString("prefs.window.title"));

        // 多重起動できないように閉じるまで実行不可とする
        executable.set(false);
        stage.setOnHidden(e -> executable.set(true));

        var tuple = FluentViewLoader.fxmlView(PreferencesView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var codeBehind = tuple.getCodeBehind();
        codeBehind.setPreferencesStage(stage);

        var view = tuple.getView();
        var scene = new Scene(view);
        stage.setScene(scene);
        stage.showAndWait();
    }
}
