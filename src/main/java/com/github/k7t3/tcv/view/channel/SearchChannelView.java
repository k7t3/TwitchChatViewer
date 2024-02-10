package com.github.k7t3.tcv.view.channel;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.vm.channel.FoundChannelViewModel;
import com.github.k7t3.tcv.vm.channel.SearchChannelViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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
    private ListView<FoundChannelViewModel> foundChannels;

    @FXML
    private StackPane container;

    private ProgressIndicator loadingMask;

    @InjectViewModel
    private SearchChannelViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        keywordField.setLeft(new FontIcon(Feather.SEARCH));
        keywordField.textProperty().bindBidirectional(viewModel.keywordProperty());
        keywordField.textProperty().addListener((ob, o, n) -> viewModel.search());
        liveSwitch.selectedProperty().bindBidirectional(viewModel.onlyLiveProperty());
        liveSwitch.selectedProperty().addListener((ob, o, n) -> viewModel.search(TimeUnit.SECONDS, 0));

        foundChannels.setCellFactory(param -> new FoundChannelListCell());
        foundChannels.setItems(viewModel.getChannels());
        foundChannels.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                joinChat();
        });
        foundChannels.setOnKeyPressed(e -> {
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

        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.5));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
        });

        initContextMenu();
    }

    private void joinChat() {
        var channel = foundChannels.getSelectionModel().getSelectedItem();
        if (channel == null) return;
        channel.joinChatAsync();
    }

    private void initContextMenu() {
        var openChat = new MenuItem(Resources.getString("search.open.chat"));
        openChat.setOnAction(e -> {
            var channel = foundChannels.getSelectionModel().getSelectedItem();
            if (channel == null) return;

            channel.joinChatAsync();
        });

        var openBrowser = new MenuItem(Resources.getString("search.open.browser"));
        openBrowser.setOnAction(e -> {
            var channel = foundChannels.getSelectionModel().getSelectedItem();
            if (channel == null) return;

            var nest = channel.openChannelPageOnBrowser();
            nest.setOnSucceeded(e2 -> {
                if (nest.getValue()) return;

                var alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Failed to open Browser!");
                alert.setContentText("Failed to Open Browser!");
                alert.show();
            });
        });

        var contextMenu = new ContextMenu(openChat, new SeparatorMenuItem(), openBrowser);
        foundChannels.setContextMenu(contextMenu);
    }

    public CustomTextField getKeywordField() {
        return keywordField;
    }
}
