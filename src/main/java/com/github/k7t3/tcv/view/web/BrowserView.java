package com.github.k7t3.tcv.view.web;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.web.BrowserViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.ResourceBundle;

public class BrowserView implements FxmlView<BrowserViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private Button reloadButton;

    @FXML
    private Button closeButton;

    @FXML
    private Label titleLabel;

    @FXML
    private WebView webView;

    @FXML
    private ProgressIndicator loadingMask;

    @InjectViewModel
    private BrowserViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        viewModel.setEngine(webView.getEngine());

        webView.setContextMenuEnabled(false);
        webView.setFontSmoothingType(FontSmoothingType.GRAY);

        titleLabel.textProperty().bind(viewModel.getEngine().titleProperty());

        reloadButton.setOnAction(e -> webView.getEngine().reload());
        reloadButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE);

        closeButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.DANGER);

        loadingMask.visibleProperty().bind(viewModel.getEngine().getLoadWorker().runningProperty());
    }

    public Button getCloseButton() {
        return closeButton;
    }

}
