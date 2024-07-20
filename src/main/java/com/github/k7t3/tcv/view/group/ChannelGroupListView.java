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
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.application.Platform;
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
import java.util.function.Supplier;

public class ChannelGroupListView implements FxmlView<ChannelGroupListViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private Label title;

    @FXML
    private CustomTextField filterField;

    @FXML
    private StackPane channelGroupListContainer;

    @FXML
    private ComboBox<ChannelGroupListViewModel.Comparators> comparatorsComboBox;

    @FXML
    private ToggleSwitch descendingSwitch;

    @InjectViewModel
    private ChannelGroupListViewModel viewModel;

    private VirtualFlow<ChannelGroup, ChannelGroupListCell> virtualFlow;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        title.getStyleClass().addAll(Styles.TITLE_3);

        // フィルタ用キーワード
        viewModel.filterProperty().bindBidirectional(filterField.textProperty());

        // フィルタフィールドのアイコン
        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));
        filterField.setRight(clearIcon);
        filterField.setLeft(new FontIcon(Feather.SEARCH));

        // コンパレータ
        comparatorsComboBox.getItems().setAll(ChannelGroupListViewModel.Comparators.values());
        comparatorsComboBox.valueProperty().addListener((ob, o, n) -> filterUpdate());
        descendingSwitch.selectedProperty().addListener((ob, o, n) -> filterUpdate());

        var prefs = AppPreferences.getInstance();

        // VirtualFlow
        Supplier<VirtualFlow<ChannelGroup, ChannelGroupListCell>> vfInjector = () -> virtualFlow; // 力技ではあるか？
        virtualFlow = VirtualFlow.createVertical(viewModel.getChannelGroups(), g -> new ChannelGroupListCell(prefs.getGeneralPreferences(), g, viewModel, vfInjector));
        channelGroupListContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // ダイアログとして表示するための初期化
        JavaFXHelper.initAsInnerWindow(root, 0.7, 0.9);

        // 設定とバインド
        bindPreferences();
    }

    private void bindPreferences() {
        var preferences = AppPreferences.getInstance().getStatePreferences();
        descendingSwitch.selectedProperty().bindBidirectional(preferences.groupOrderDescendingProperty());

        var comparatorName = preferences.getGroupOrderItem();
        var comparator = ChannelGroupListViewModel.Comparators.valueOf(comparatorName);
        comparatorsComboBox.getSelectionModel().select(comparator);
        preferences.groupOrderItemProperty().bind(comparatorsComboBox.valueProperty().map(Enum::name));
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
        Platform.runLater(() -> virtualFlow.show(0));
        filterField.requestFocus();
    }

}
