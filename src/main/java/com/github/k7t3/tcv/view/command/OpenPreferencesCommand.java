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

import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import com.github.k7t3.tcv.app.prefs.PreferencesViewModel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.function.Supplier;
import javafx.beans.value.ChangeListener;
import java.util.HashSet;
import java.util.Set;

public class OpenPreferencesCommand extends BasicCommand implements AutoCloseable {
    private final Supplier<Stage> owner;
    private final Function<Stage, PreferencesViewModel> modelFactory;
    private final Function<Stage, PreferencesView> viewFactory;
    private final Supplier<Stage> stageFactory;
    private Editor active;
    private final Set<Editor> pending = new HashSet<>();
    private boolean closed;

    public OpenPreferencesCommand(Supplier<Stage> owner, Function<Stage, PreferencesViewModel> models,
                                  Function<Stage, PreferencesView> views, Supplier<Stage> stages) {
        this.owner = java.util.Objects.requireNonNull(owner);
        this.modelFactory = java.util.Objects.requireNonNull(models);
        this.viewFactory = java.util.Objects.requireNonNull(views);
        this.stageFactory = java.util.Objects.requireNonNull(stages);
    }

    @Override
    public void execute() {
        if (closed) throw new IllegalStateException("preferences command is closed");
        if (isNotExecutable() || active != null) return;
        var opening = new Editor();
        active = opening;
        try {
            executable.set(false);
            if (releaseIfRetired(opening)) return;
            opening.stage = stageFactory.get();
            if (releaseIfRetired(opening)) return;
            opening.stage.initOwner(owner.get());
            opening.stage.initModality(Modality.WINDOW_MODAL);
            opening.stage.getIcons().addAll(Resources.getIcons());
            opening.stage.setTitle(Resources.getString("prefs.window.title"));
            if (releaseIfRetired(opening)) return;
            opening.model = modelFactory.apply(opening.stage);
            if (releaseIfRetired(opening)) return;
            opening.controller = viewFactory.apply(opening.stage);
            if (releaseIfRetired(opening)) return;
            var tuple = FluentViewLoader.fxmlView(PreferencesView.class).codeBehind(opening.controller)
                    .viewModel(opening.model).resourceBundle(Resources.getResourceBundle()).load();
            if (releaseIfRetired(opening)) return;
            opening.controller.setPreferencesStage(opening.stage);
            if (releaseIfRetired(opening)) return;
            opening.stage.setOnHidden(event -> release(opening));
            opening.stage.setScene(new Scene(tuple.getView()));
            if (releaseIfRetired(opening)) return;
            opening.stage.show();
            releaseIfRetired(opening);
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, () -> release(opening));
        }
    }

    private boolean releaseIfRetired(Editor opening) {
        if (!closed && active == opening) return false;
        release(opening);
        return true;
    }

    private void release(Editor owned) {
        if (active == owned) active = null;
        CloseActions.runAll(() -> { if (owned.controller != null) owned.controller.close(); },
                () -> { if (owned.model != null) owned.model.close(); },
                () -> {
                    if (owned.stage == null) return;
                    owned.stage.setOnHidden(null);
                    owned.stage.hide();
                    owned.stage.setScene(null);
                }, () -> {
                    if (!closed && owned.model != null && owned.model.isSavePending()) {
                        pending.add(owned);
                        owned.pendingChanged = (observable, old, value) -> { if (!value) releasePending(owned); };
                        owned.model.savePendingProperty().addListener(owned.pendingChanged);
                    }
                    executable.set(!closed && active == null && pending.isEmpty());
                });
    }

    private void releasePending(Editor owned) {
        if (owned.pendingChanged != null) owned.model.savePendingProperty().removeListener(owned.pendingChanged);
        owned.pendingChanged = null;
        pending.remove(owned);
        executable.set(!closed && active == null && pending.isEmpty());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        executable.set(false);
        var owned = active;
        if (owned != null) release(owned);
        for (var editor : Set.copyOf(pending)) releasePending(editor);
    }

    private static final class Editor {
        Stage stage;
        PreferencesView controller;
        PreferencesViewModel model;
        ChangeListener<Boolean> pendingChanged;
    }
}
