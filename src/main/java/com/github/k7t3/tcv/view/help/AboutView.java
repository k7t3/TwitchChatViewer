package com.github.k7t3.tcv.view.help;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.help.AboutViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

public class AboutView implements FxmlView<AboutViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private Label titleLabel;

    @FXML
    private Label versionLabel;

    @FXML
    private Button gitHubButton;

    @FXML
    private ImageView iconImageView;

    @FXML
    private Hyperlink licensePolicyLink;

    @FXML
    private Hyperlink librariesLink;

    @FXML
    private Button okButton;

    @InjectViewModel
    private AboutViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        titleLabel.getStyleClass().addAll(Styles.TITLE_3);
        gitHubButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    }

    @FXML
    private void browseGitHubPage() {
        viewModel.browseGitHubPage();
    }

    @FXML
    private void browseLicensePolicyPage() {
        viewModel.browseLicenseWithSecurityPolicyPage();
    }

    @FXML
    private void browseLibrariesPage() {
        viewModel.browseLibrariesPage();
    }

    public Button getOkButton() {
        return okButton;
    }

}
