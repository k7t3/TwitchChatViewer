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

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.clip.PostedClipRepositoryView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Parent;

public class OpenClipCommand extends OwnedModalCommand {
    private final PostedClipRepository repository;
    public OpenClipCommand(ModalPane pane, PostedClipRepository repository, ObservableValue<Boolean> condition) {
        super(pane, condition, Side.BOTTOM); this.repository = repository;
    }
    @Override protected Parent load(ModalSession session) {
        var controller = new PostedClipRepositoryView(); session.own(controller::close);
        return FluentViewLoader.fxmlView(PostedClipRepositoryView.class).codeBehind(controller).viewModel(repository)
                .resourceBundle(Resources.getResourceBundle()).load().getView();
    }
}
