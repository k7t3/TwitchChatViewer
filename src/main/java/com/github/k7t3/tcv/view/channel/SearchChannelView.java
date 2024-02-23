package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.channel.FoundChannelViewModel;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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

public class SearchChannelView implements FxmlView<SearchChannelViewModel>, Initializable {

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

    @InjectViewModel
    private SearchChannelViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setKeyword(null));

        keywordField.setLeft(new FontIcon(Feather.SEARCH));
        keywordField.setRight(clearIcon);
        keywordField.textProperty().bindBidirectional(viewModel.keywordProperty());
        keywordField.textProperty().addListener((ob, o, n) -> viewModel.search());
        liveSwitch.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());
        liveSwitch.selectedProperty().addListener((ob, o, n) -> viewModel.search(TimeUnit.SECONDS, 0));

        channelsListView.setCellFactory(param -> new FoundChannelListCell());
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

        viewModel.loadingProperty().addListener((ob, o, n) -> {
            if (n)
                container.getChildren().add(loadingMask);
            else
                container.getChildren().remove(loadingMask);
        });

//        root.parentProperty().addListener((ob, o, n) -> {
//            if (n == null) return;
//            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.5));
//            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
//        });
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
