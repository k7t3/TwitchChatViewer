package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.prefs.ChatPreferencesViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.view.chat.ChatFont;
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
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class ChatPreferencesView implements PreferencesTabView<ChatPreferencesViewModel> {

    @FXML
    private ComboBox<Font> fontComboBox;

    @FXML
    private ComboBox<Double> fontSizeComboBox;

    @FXML
    private Label defaultPreviewLabel;

    @FXML
    private Label previewLabel;

    @FXML
    private ToggleSwitch showNameSwitch;

    @FXML
    private ToggleSwitch showBadgeSwitch;

    @FXML
    private ComboBox<Integer> chatCacheSizeComboBox;

    @InjectViewModel
    private ChatPreferencesViewModel viewModel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initFontComboBox();
        initFontSizeComboBox();
        initChatCacheSizeComboBox();

        // 言語が英語のときはプレビューが重複するため非表示にする
        var locale = Locale.getDefault();
        if ("en".equals(locale.getLanguage())) {
            previewLabel.setVisible(false);
            previewLabel.setManaged(false);
        }

        showNameSwitch.selectedProperty().bindBidirectional(viewModel.showUserNameProperty());
        showBadgeSwitch.selectedProperty().bindBidirectional(viewModel.showBadgesProperty());

        // プレビューのフォント設定
        fontComboBox.valueProperty().addListener((ob, o, n) -> loadPreviewFont());
        fontSizeComboBox.valueProperty().addListener((ob, o, n) -> loadPreviewFont());
    }

    private void loadPreviewFont() {
        var font = fontComboBox.getValue();
        var size = fontSizeComboBox.getValue();
        font = Font.font(font.getFamily(), size);
        previewLabel.setFont(font);
        defaultPreviewLabel.setFont(font);
    }

    private void initFontComboBox() {
        fontComboBox.setDisable(true);
        defaultPreviewLabel.setDisable(true);
        previewLabel.setDisable(true);

        fontComboBox.setConverter(new FontStringConverter(fontComboBox.getItems()));
        fontComboBox.setCellFactory(param -> new FontComboBoxCell());

        var fontLoader = FXTask.task(() -> {
            TimeUnit.MILLISECONDS.sleep(400);
            return Font.getFamilies().stream().map(Font::font).toList();
        });
        fontLoader.setOnSucceeded(e -> {
            fontComboBox.getItems().addAll(fontLoader.getValue());

            fontComboBox.setDisable(false);
            defaultPreviewLabel.setDisable(false);
            previewLabel.setDisable(false);

            fontComboBox.getSelectionModel().select(viewModel.getFont());
            viewModel.fontProperty().bind(fontComboBox.valueProperty());
        });
        TaskWorker.getInstance().submit(fontLoader);
    }

    private void initFontSizeComboBox() {
        fontSizeComboBox.getItems().addAll(ChatPreferencesViewModel.FONT_SIZES);
        fontSizeComboBox.getSelectionModel().select(viewModel.getFontSize());
        viewModel.fontSizeProperty().bind(fontSizeComboBox.valueProperty());
    }

    private void initChatCacheSizeComboBox() {
        chatCacheSizeComboBox.getItems().setAll(ChatPreferencesViewModel.CHAT_CACHE_SIZES);
        chatCacheSizeComboBox.getSelectionModel().select((Integer) viewModel.getChatCacheSize());
        viewModel.chatCacheSizeProperty().bind(chatCacheSizeComboBox.valueProperty());
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
