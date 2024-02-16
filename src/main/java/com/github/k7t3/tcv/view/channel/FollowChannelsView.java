package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.channel.FollowChannelViewModel;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class FollowChannelsView implements FxmlView<FollowChannelsViewModel>, Initializable {

    @FXML
    private ListView<FollowChannelViewModel> channels;

    @FXML
    private CustomTextField searchField;

    @FXML
    private ToggleSwitch onlyLiveSwitch;

    @InjectViewModel
    private FollowChannelsViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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

        onlyLiveSwitch.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());
    }

}
