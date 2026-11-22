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

package com.github.k7t3.tcv.view.prefs;

import java.util.function.Consumer;

import java.util.function.Supplier;

import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.*;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.ViewResources;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class PreferencesView implements FxmlView<PreferencesViewModel>, Initializable, AutoCloseable {
    /** ページと保存の適用先は起動側で接続し、生成されたページモデルはViewが所有する。 */
    public record Pages(Supplier<GeneralPreferencesViewModel> general, Supplier<ChatPreferencesViewModel> chat,
                        Supplier<KeywordFilterViewModel> keywords, Supplier<UserFilterViewModel> users,
                        Supplier<KeyBindingPreferencesViewModel> keys,
                        Supplier<Consumer<SettingsSaveService.SavedSettings>> prepareCommit) {}

    private final Pages pages;
    private final ViewResources pageResources = new ViewResources();
    private final ViewResources modelResources = new ViewResources();
    private boolean closed;

    public PreferencesView(Pages pages) {
        this.pages = java.util.Objects.requireNonNull(pages);
    }

    @FXML
    private Pane root;

    @FXML
    private TreeView<PreferencesPage<?>> pagesTreeView;

    @FXML
    private Label pageHeading;

    @FXML
    private StackPane contentPane;

    @FXML
    private ButtonBar buttonBar;

    @FXML
    private Button enterButton;

    @FXML
    private Button cancelButton;

    @InjectViewModel
    private PreferencesViewModel viewModel;

    private TreeItem<PreferencesPage<?>> pagesRoot;

    private List<PreferencesViewModelBase> viewModels;
    private Map<PreferencesViewModelBase, TreeItem<PreferencesPage<?>>> pageMap;
    private Map<PreferencesPage<?>, Node> viewMap;
    private ChangeListener<TreeItem<PreferencesPage<?>>> pageSelection;

    private Stage preferencesStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (closed) return;
        viewModels = new ArrayList<>();
        pageMap = new HashMap<>();
        viewMap = new HashMap<>();
        pagesRoot = new TreeItem<>();

        pagesTreeView.setRoot(pagesRoot);
        pagesTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        pagesTreeView.setCellFactory(v -> new PreferencesPageTreeCell());
        pageSelection = (ob, o, n) -> {
            if (closed) return;
            pageHeading.setText(n == null ? "" : n.getValue().getName());
            if (n == null) {
                contentPane.getChildren().clear();
            } else {
                var node = viewMap.get(n.getValue());
                contentPane.getChildren().setAll(node);
            }
        };
        pagesTreeView.getSelectionModel().selectedItemProperty().addListener(pageSelection);

        for (var load : List.<Runnable>of(this::loadGeneralViewModel, this::loadChatViewModel,
                this::loadFilterViewModel, this::loadUserFilterViewModel, this::loadKeyBindingViewModel)) {
            if (closed) return;
            load.run();
        }
        if (closed) return;

        initButtons();

        pagesTreeView.getSelectionModel().select(0);
    }

    public void setPreferencesStage(Stage preferencesStage) {
        this.preferencesStage = preferencesStage;
    }

    private <M extends PreferencesViewModelBase> M ownModel(M model) {
        modelResources.add(model::close);
        if (!closed) viewModels.add(model);
        return model;
    }

    private <P extends PreferencesPage<?>> P ownPage(P page) {
        pageResources.add(page::close);
        return page;
    }

    private void loadGeneralViewModel() {
        var generalViewModel = ownModel(pages.general().get());
        if (closed) return;
        var controller = ownPage(new GeneralPreferencesView());
        if (closed) return;

        var tuple = FluentViewLoader.fxmlView(GeneralPreferencesView.class)
                .codeBehind(controller)
                .viewModel(generalViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        if (closed) return;

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(generalViewModel, item);
    }

    private void loadChatViewModel() {
        var chatViewModel = ownModel(pages.chat().get());
        if (closed) return;
        var controller = ownPage(new ChatPreferencesView());
        if (closed) return;

        var tuple = FluentViewLoader.fxmlView(ChatPreferencesView.class)
                .codeBehind(controller)
                .viewModel(chatViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        if (closed) return;

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(chatViewModel, item);
    }

    private void loadFilterViewModel() {
        var filterViewModel = ownModel(pages.keywords().get());
        if (closed) return;
        var controller = ownPage(new KeywordFilterView());
        if (closed) return;
        var tuple = FluentViewLoader.fxmlView(KeywordFilterView.class).codeBehind(controller)
                .viewModel(filterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        if (closed) return;

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(filterViewModel, item);
    }

    private void loadUserFilterViewModel() {
        var userFilterViewModel = ownModel(pages.users().get());
        if (closed) return;
        var controller = ownPage(new UserFilterView());
        if (closed) return;
        var tuple = FluentViewLoader.fxmlView(UserFilterView.class).codeBehind(controller)
                .viewModel(userFilterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        if (closed) return;

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(userFilterViewModel, item);
    }

    private void loadKeyBindingViewModel() {
        var keyBindingViewModel = ownModel(pages.keys().get());
        if (closed) return;
        var controller = ownPage(new KeyBindingPreferencesView());
        if (closed) return;
        var tuple = FluentViewLoader.fxmlView(KeyBindingPreferencesView.class).codeBehind(controller)
                .viewModel(keyBindingViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        if (closed) return;

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(keyBindingViewModel, item);
    }

    private void initButtons() {
        ButtonBar.setButtonData(enterButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        enterButton.disableProperty().bind(viewModel.busyProperty());
        contentPane.disableProperty().bind(viewModel.busyProperty());
        pagesTreeView.disableProperty().bind(viewModel.busyProperty());
        cancelButton.setOnAction(e -> preferencesStage.close());
        enterButton.setOnAction(e -> save());
    }

    private void save() {
        if (closed || viewModel.isBusy()) return;
        for (var viewModel : viewModels) {
            if (!viewModel.canSync()) {
                var page = pageMap.get(viewModel);
                pagesTreeView.getSelectionModel().select(page);

                var view = viewMap.get(page.getValue());
                Animations.flash(view).play();
                return;
            }
        }

        try {
            var builder = new SettingsDraft.Builder();
            viewModels.forEach(model -> model.prepare(builder));
            var apply = pages.prepareCommit().get();
            viewModel.saveAsync(builder.build(), apply).onDone(() -> { if (!closed) preferencesStage.close(); });
        } catch (RuntimeException | Error failure) { ExceptionHandler.handle(preferencesStage, failure); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(
                () -> {
                    if (pagesTreeView != null && pageSelection != null)
                        pagesTreeView.getSelectionModel().selectedItemProperty().removeListener(pageSelection);
                    pageSelection = null;
                },
                pageResources::close,
                modelResources::close,
                () -> { if (viewModel != null) viewModel.close(); },
                () -> {
                    if (enterButton != null) { enterButton.disableProperty().unbind(); enterButton.setOnAction(null); }
                    if (cancelButton != null) cancelButton.setOnAction(null);
                    if (contentPane != null) { contentPane.disableProperty().unbind(); contentPane.getChildren().clear(); }
                    if (pagesTreeView != null) { pagesTreeView.disableProperty().unbind(); pagesTreeView.setRoot(null); }
                    if (viewMap != null) viewMap.clear();
                    if (pageMap != null) pageMap.clear();
                    if (viewModels != null) viewModels.clear();
                });
    }

}
