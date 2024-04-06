package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.prefs.ChatPreferencesViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.ChatFont;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.prefs.font.FontComboBoxCell;
import com.github.k7t3.tcv.view.prefs.font.FontStringConverter;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class ChatPreferencesView implements PreferencesTabView<ChatPreferencesViewModel> {

    @FXML
    private ComboBox<ChatFont> fontComboBox;

    @FXML
    private Label defaultPreviewLabel;

    @FXML
    private Label previewLabel;

    @FXML
    private ToggleSwitch showNameSwitch;

    @FXML
    private ToggleSwitch showBadgeSwitch;

    @InjectViewModel
    private ChatPreferencesViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initFontComboBox();

        // 言語が英語のときはプレビューが重複するため非表示にする
        var locale = Locale.getDefault();
        if ("en".equals(locale.getLanguage())) {
            previewLabel.setVisible(false);
            previewLabel.setManaged(false);
        }

        showNameSwitch.selectedProperty().bindBidirectional(viewModel.showUserNameProperty());
        showBadgeSwitch.selectedProperty().bindBidirectional(viewModel.showBadgesProperty());
    }

    private void initFontComboBox() {
        fontComboBox.setDisable(true);
        defaultPreviewLabel.setDisable(true);
        previewLabel.setDisable(true);

        fontComboBox.setConverter(new FontStringConverter(fontComboBox.getItems()));
        fontComboBox.setCellFactory(param -> new FontComboBoxCell());

        var fontLoader = FXTask.task(() -> {
            TimeUnit.MILLISECONDS.sleep(400);
            return Font.getFamilies().stream().map(ChatFont::new).toList();
        });
        fontLoader.setOnSucceeded(e -> {
            fontComboBox.getItems().addAll(fontLoader.getValue());

            fontComboBox.setDisable(false);
            defaultPreviewLabel.setDisable(false);
            previewLabel.setDisable(false);

            fontComboBox.getSelectionModel().select(viewModel.getFont());
            viewModel.fontProperty().bind(fontComboBox.valueProperty());

            // プレビューのフォント設定
            defaultPreviewLabel.fontProperty().bind(fontComboBox.valueProperty().map(ChatFont::getFont));
            previewLabel.fontProperty().bind(fontComboBox.valueProperty().map(ChatFont::getFont));
        });
        TaskWorker.getInstance().submit(fontLoader);
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.chat");
    }

    @Override
    public Node getGraphic() {
        return new FontIcon(Feather.MESSAGE_CIRCLE);
    }

}
