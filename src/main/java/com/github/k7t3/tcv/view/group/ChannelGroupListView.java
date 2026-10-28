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

package com.github.k7t3.tcv.view.group;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupOrder;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.value.ChangeListener;
import javafx.util.Subscription;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class ChannelGroupListView implements FxmlView<ChannelGroupListViewModel>, Initializable, AutoCloseable {
    private final AppPreferences preferences;
    private boolean closed;
    private Subscription sizing;
    private FontIcon clearIcon;
    private final ChangeListener<ChannelGroupOrder> orderChanged = (observable, old, value) -> orderChanged(value);
    private void orderChanged(ChannelGroupOrder value) {
        if (closed) return;
        if (value != null) preferences.getStatePreferences().setGroupOrderItem(value.name());
        filterUpdate();
    }
    private final ChangeListener<Boolean> descendingChanged = (observable, old, value) -> { if (!closed) filterUpdate(); };

    public ChannelGroupListView(AppPreferences preferences) { this.preferences = preferences; }


    @FXML
    private Pane root;

    @FXML
    private Label title;

    @FXML
    private CustomTextField filterField;

    @FXML
    private StackPane channelGroupListContainer;

    @FXML
    private ComboBox<ChannelGroupOrder> comparatorsComboBox;

    @FXML
    private ToggleSwitch descendingSwitch;

    @InjectViewModel
    private ChannelGroupListViewModel viewModel;

    private VirtualFlow<ChannelGroup, ChannelGroupListCell> virtualFlow;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (closed) throw new IllegalStateException("group view is closed");
        title.getStyleClass().addAll(Styles.TITLE_3);

        // フィルタ用キーワード
        viewModel.filterProperty().bindBidirectional(filterField.textProperty());

        // フィルタフィールドのアイコン
        clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));
        filterField.setRight(clearIcon);
        filterField.setLeft(new FontIcon(Feather.SEARCH));

        // コンパレータ
        comparatorsComboBox.getItems().setAll(ChannelGroupOrder.values());
        comparatorsComboBox.valueProperty().addListener(orderChanged);
        descendingSwitch.selectedProperty().addListener(descendingChanged);

        var prefs = preferences;

        // VirtualFlow
        virtualFlow = VirtualFlow.createVertical(viewModel.getChannelGroups(),
                group -> new ChannelGroupListCell(prefs.getGeneralPreferences(), group, viewModel, this::showTop));
        channelGroupListContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // ダイアログとして表示するための初期化
        sizing = JavaFXHelper.initAsInnerWindow(root, 0.7, 0.9);

        // 設定とバインド
        bindPreferences();
    }

    private void bindPreferences() {
        var preferences = this.preferences.getStatePreferences();
        descendingSwitch.selectedProperty().bindBidirectional(preferences.groupOrderDescendingProperty());

        var comparatorName = preferences.getGroupOrderItem();
        var comparator = ChannelGroupOrder.valueOf(comparatorName);
        comparatorsComboBox.getSelectionModel().select(comparator);
    }

    private void filterUpdate() {
        var comparator = comparatorsComboBox.getValue();
        if (comparator == null) {
            viewModel.setComparator(null);
            return;
        }
        if (descendingSwitch.isSelected()) {
            viewModel.setComparator(comparator.reversed());
        } else {
            viewModel.setComparator(comparator);
        }
    }

    /**
     * 要素の先頭から表示するようにする
     */
    public void onOpened() {
        if (closed || virtualFlow == null) return;
        showTop();
        filterField.requestFocus();
    }

    private void showTop() {
        if (!closed && virtualFlow != null && !viewModel.getChannelGroups().isEmpty()) virtualFlow.show(0);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> {
            if (sizing != null) { sizing.unsubscribe(); sizing = null; }
        }, () -> {
            if (comparatorsComboBox != null) comparatorsComboBox.valueProperty().removeListener(orderChanged);
            if (descendingSwitch != null) {
                descendingSwitch.selectedProperty().removeListener(descendingChanged);
                descendingSwitch.selectedProperty().unbindBidirectional(preferences.getStatePreferences().groupOrderDescendingProperty());
            }
            if (filterField != null && viewModel != null) viewModel.filterProperty().unbindBidirectional(filterField.textProperty());
            if (clearIcon != null) clearIcon.setOnMouseClicked(null);
        }, () -> {
            var flow = virtualFlow; virtualFlow = null;
            if (flow != null) flow.dispose();
        }, () -> { if (channelGroupListContainer != null) channelGroupListContainer.getChildren().clear(); });
    }

}
