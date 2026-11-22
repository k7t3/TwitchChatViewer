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

package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.channel.FoundChannelViewModel;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class SearchChannelView implements FxmlView<SearchChannelViewModel>, Initializable, AutoCloseable {
    private final java.util.List<FoundChannelListCell> cells = new java.util.ArrayList<>();

    @FXML
    private Pane root;

    @FXML
    private CustomTextField keywordField;

    @FXML
    private ToggleSwitch liveSwitch;

    @FXML
    private ListView<FoundChannelViewModel> channelsListView;

    @FXML
    private StackPane container;

    private ProgressIndicator loadingMask;
    private FontIcon clearIcon;
    private ChangeListener<String> keywordListener;
    private ChangeListener<Boolean> liveListener;
    private ChangeListener<Boolean> loadingListener;
    private ChangeListener<Parent> parentListener;
    private boolean closed;

    @InjectViewModel
    private SearchChannelViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setKeyword(null));

        keywordField.setLeft(new FontIcon(Feather.SEARCH));
        keywordField.setRight(clearIcon);
        keywordField.textProperty().bindBidirectional(viewModel.keywordProperty());
        keywordListener = (ob, o, n) -> viewModel.search();
        keywordField.textProperty().addListener(keywordListener);
        liveSwitch.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());
        liveListener = (ob, o, n) -> viewModel.search(TimeUnit.SECONDS, 0);
        liveSwitch.selectedProperty().addListener(liveListener);

        channelsListView.setCellFactory(param -> { var cell = new FoundChannelListCell(); cells.add(cell); return cell; });
        channelsListView.setItems(viewModel.getChannels());
        channelsListView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                joinChat();
        });
        channelsListView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                joinChat();
        });

        loadingMask = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        loadingMask.getStyleClass().add("loading-mask");
        loadingMask.maxWidthProperty().bind(container.widthProperty().multiply(0.5));
        loadingMask.maxHeightProperty().bind(container.heightProperty().multiply(0.5));

        loadingListener = (ob, o, n) -> updateLoading(n);
        viewModel.loadingProperty().addListener(loadingListener);
        updateLoading(viewModel.isLoading());

        parentListener = (ob, o, n) -> {
            if (n == null) {
                close();
                return;
            }
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.5));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
        };
        root.parentProperty().addListener(parentListener);
    }

    private void updateLoading(boolean loading) {
        if (closed) return;
        if (loading && !container.getChildren().contains(loadingMask)) container.getChildren().add(loadingMask);
        else if (!loading) container.getChildren().remove(loadingMask);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> {
            if (keywordField != null && keywordListener != null) keywordField.textProperty().removeListener(keywordListener);
            if (liveSwitch != null && liveListener != null) liveSwitch.selectedProperty().removeListener(liveListener);
            if (viewModel != null && loadingListener != null) viewModel.loadingProperty().removeListener(loadingListener);
        }, () -> {
            if (viewModel == null) return;
            if (keywordField != null) keywordField.textProperty().unbindBidirectional(viewModel.keywordProperty());
            if (liveSwitch != null) liveSwitch.selectedProperty().unbindBidirectional(viewModel.onlyLiveProperty());
        }, () -> {
            if (root == null) return;
            if (parentListener != null) root.parentProperty().removeListener(parentListener);
            root.prefWidthProperty().unbind();
            root.prefHeightProperty().unbind();
        }, () -> {
            if (loadingMask == null) return;
            loadingMask.maxWidthProperty().unbind();
            loadingMask.maxHeightProperty().unbind();
            if (container != null) container.getChildren().remove(loadingMask);
        }, () -> {
            if (channelsListView == null) return;
            channelsListView.setItems(null);
            channelsListView.setCellFactory(null);
            channelsListView.setOnMouseClicked(null);
            channelsListView.setOnKeyPressed(null);
        }, () -> {
            var owned = java.util.List.copyOf(cells); cells.clear();
            CloseActions.runAll(owned.stream().map(cell -> (Runnable) cell::close).toArray(Runnable[]::new));
        }, () -> { if (clearIcon != null) clearIcon.setOnMouseClicked(null); },
                () -> { if (viewModel != null) viewModel.close(); });
    }

    private void joinChat() {
        var channel = channelsListView.getSelectionModel().getSelectedItem();
        if (channel == null) return;
        channel.joinChatAsync();
    }

    public CustomTextField getKeywordField() {
        return keywordField;
    }
}
