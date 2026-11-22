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

import java.util.function.Function;

import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.help.AboutViewModel;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.help.AboutView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.function.Supplier;

/** FXML 読込前から About の窓・コントローラー・モデルを所有する。 */
public class OpenAboutCommand extends BasicCommand implements AutoCloseable {
    private final Supplier<Stage> owner;
    private final Function<Stage, AboutViewModel> modelFactory;
    private final Supplier<AboutView> viewFactory;
    private final Supplier<Stage> stageFactory;
    private Window active;
    private boolean closed;

    public OpenAboutCommand(Supplier<Stage> owner, Function<Stage, AboutViewModel> models,
                            Supplier<AboutView> views, Supplier<Stage> stages) {
        this.owner = java.util.Objects.requireNonNull(owner);
        this.modelFactory = java.util.Objects.requireNonNull(models);
        this.viewFactory = java.util.Objects.requireNonNull(views);
        this.stageFactory = java.util.Objects.requireNonNull(stages);
    }

    @Override public void execute() {
        if (closed || isNotExecutable() || active != null) return;
        var opening = new Window();
        active = opening;
        executable.set(false);
        try {
            if (!isCurrent(opening)) { release(opening); return; }
            opening.stage = stageFactory.get();
            if (!isCurrent(opening)) { release(opening); return; }
            opening.stage.initOwner(owner.get());
            opening.stage.initModality(Modality.WINDOW_MODAL);
            opening.stage.getIcons().addAll(Resources.getIcons());
            opening.stage.setTitle(Resources.getString("about.application"));
            opening.stage.setResizable(false);
            opening.model = modelFactory.apply(opening.stage);
            if (!isCurrent(opening)) { release(opening); return; }
            opening.controller = viewFactory.get();
            if (!isCurrent(opening)) { release(opening); return; }
            var tuple = FluentViewLoader.fxmlView(AboutView.class).codeBehind(opening.controller).viewModel(opening.model)
                    .resourceBundle(Resources.getResourceBundle()).load();
            if (!isCurrent(opening)) { release(opening); return; }
            opening.controller.getOkButton().setOnAction(event -> release(opening));
            opening.stage.setOnHidden(event -> release(opening));
            opening.stage.setScene(new Scene(tuple.getView()));
            if (!isCurrent(opening)) { release(opening); return; }
            opening.stage.show();
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, () -> release(opening));
        }
    }

    private boolean isCurrent(Window window) { return !closed && !window.closed && active == window; }

    private void release(Window owned) {
        owned.closed = true;
        if (active == owned) active = null;
        var stage = owned.stage;
        var controller = owned.controller;
        var model = owned.model;
        owned.stage = null;
        owned.controller = null;
        owned.model = null;
        CloseActions.runAll(() -> { if (controller != null) controller.close(); },
                () -> { if (model != null) model.close(); },
                () -> { if (stage != null) { stage.setOnHidden(null); stage.hide(); stage.setScene(null); stage.getIcons().clear(); } },
                () -> executable.set(!closed && active == null));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        executable.set(false);
        if (active != null) release(active);
    }

    private static final class Window {
        Stage stage;
        AboutView controller;
        AboutViewModel model;
        boolean closed;
    }
}
