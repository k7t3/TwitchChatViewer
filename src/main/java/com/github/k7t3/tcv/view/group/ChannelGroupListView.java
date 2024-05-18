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
        comparatorsComboBox.getSelectionModel().select(0);

        var prefs = AppPreferences.getInstance();

        // VirtualFlow
        virtualFlow = VirtualFlow.createVertical(viewModel.getChannelGroups(), g -> new ChannelGroupListCell(prefs.getGeneralPreferences(), g, viewModel));
        channelGroupListContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // ダイアログとして表示するための初期化
        JavaFXHelper.initAsInnerWindow(root, 0.7, 0.9);
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
    }

}
