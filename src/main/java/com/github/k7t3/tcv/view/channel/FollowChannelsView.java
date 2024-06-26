package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class FollowChannelsView implements FxmlView<FollowChannelsViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private ListView<TwitchChannelViewModel> channels;

    @FXML
    private CustomTextField searchField;

    @FXML
    private ToggleButton drawLeftToggle;

    @InjectViewModel
    private FollowChannelsViewModel viewModel;

    private enum ViewState { OPEN, CLOSE }

    private ObjectProperty<ViewState> state;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        state = new SimpleObjectProperty<>(ViewState.OPEN);

        var helper = AppHelper.getInstance();

        // 認証されていないときはrootから無効化
        root.disableProperty().bind(helper.authorizedProperty().not());

        channels.setCellFactory(param -> new FollowChannelListCell(viewModel.visibleFullyProperty()));
        channels.disableProperty().bind(viewModel.loadedProperty().not());
        channels.setItems(viewModel.getFollowChannels());

        // チャンネルは複数選択することが可能
        channels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 選択しているブロードキャスターはView → ViewModelの一方向のみ
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        // ENTERキーの入力でチャットを開く
        channels.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;
            openChats();
        });

        // ダブルクリックでチャットを開く
        channels.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 2) return;
            openChats();
        });

        // Viewが選択している要素をバインド
        Bindings.bindContent(viewModel.getSelectedChannels(), channels.getSelectionModel().getSelectedItems());

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));

        searchField.textProperty().bindBidirectional(viewModel.filterProperty());
        searchField.disableProperty().bind(viewModel.loadedProperty().not());
        searchField.setRight(clearIcon);
        searchField.setLeft(new FontIcon(Feather.SEARCH));
        searchField.visibleProperty().bind(state.isEqualTo(ViewState.OPEN));
        searchField.managedProperty().bind(state.isEqualTo(ViewState.OPEN));

        var closeIcon = new FontIcon(FontAwesomeRegular.CARET_SQUARE_LEFT);
        var openIcon = new FontIcon(FontAwesomeRegular.CARET_SQUARE_RIGHT);
        drawLeftToggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        drawLeftToggle.setGraphic(closeIcon);
        drawLeftToggle.selectedProperty().addListener((ob, o, n) -> {
            if (n) {
                state.set(ViewState.CLOSE);
                drawLeftToggle.setGraphic(openIcon);
            } else {
                state.set(ViewState.OPEN);
                drawLeftToggle.setGraphic(closeIcon);
            }
        });
        viewModel.visibleFullyProperty().bind(drawLeftToggle.selectedProperty().not());

        root.prefWidthProperty().bind(state.map(s -> s == ViewState.OPEN ? 250 : 60));
        drawLeftToggle.prefWidthProperty().bind(state.map(s -> s == ViewState.OPEN ? ToggleButton.USE_COMPUTED_SIZE : 60));
    }

    private void openChats() {
        var selected = viewModel.getSelectedChannels();

        if (selected.isEmpty())
            return;

        // 選択数が多いときは本当に開くか確認する
        if (5 < selected.size()) {
            var content = Resources.getString("container.confirm.open.content.format").formatted(selected.size());
            var dialog = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
            dialog.initOwner(AppHelper.getInstance().getPrimaryStage());
            dialog.setTitle("CONFIRMATION");
            dialog.setHeaderText(Resources.getString("container.confirm.open.header"));
            var result = dialog.showAndWait().orElse(ButtonType.NO);
            if (result != ButtonType.YES) {
                return;
            }
        }

        viewModel.joinChat();
    }

}
