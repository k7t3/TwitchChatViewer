package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.channel.FollowChannelViewModel;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
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
    private ListView<FollowChannelViewModel> channels;

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

        //channels.setCellFactory(CachedViewModelCellFactory.createForJavaView(FollowChannelView.class));
        channels.setCellFactory(param -> new FollowChannelListCell());
        channels.disableProperty().bind(viewModel.loadedProperty().not());
        channels.setItems(viewModel.getFollowBroadcasters());

        // 選択しているブロードキャスターはView → ViewModelの一方向のみ
        viewModel.selectedBroadcasterProperty().bind(channels.getSelectionModel().selectedItemProperty());

        // ENTERキーの入力でチャットを開く
        channels.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;
            viewModel.joinChat();
        });

        // ダブルクリックでチャットを開く
        channels.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 2) return;
            viewModel.joinChat();
        });

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
                root.setPrefWidth(60);
                drawLeftToggle.setPrefWidth(60);
            } else {
                state.set(ViewState.OPEN);
                drawLeftToggle.setGraphic(closeIcon);
                root.setPrefWidth(250);
                drawLeftToggle.setPrefWidth(ToggleButton.USE_COMPUTED_SIZE);
            }
        });
        viewModel.visibleFullyProperty().bind(drawLeftToggle.selectedProperty().not());
    }

}
