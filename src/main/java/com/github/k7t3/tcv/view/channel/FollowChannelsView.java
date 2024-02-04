package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.vm.channel.FollowChannelViewModel;
import com.github.k7t3.tcv.vm.channel.FollowChannelsViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.utils.viewlist.CachedViewModelCellFactory;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
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
        channels.setCellFactory(CachedViewModelCellFactory.createForJavaView(FollowChannelView.class));
        channels.disableProperty().bind(viewModel.loadedProperty().not());
        channels.setItems(viewModel.getFollowBroadcasters());

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setFilter(null));

        searchField.textProperty().bindBidirectional(viewModel.filterProperty());
        searchField.disableProperty().bind(viewModel.loadedProperty().not());
        searchField.setRight(clearIcon);
        searchField.setLeft(new FontIcon(Feather.SEARCH));

        onlyLiveSwitch.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());
    }

}
