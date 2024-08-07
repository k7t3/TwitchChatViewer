/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.ChatPreferencesViewModel;
import com.github.k7t3.tcv.app.prefs.FontFamily;
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

public class ChatPreferencesView implements PreferencesPage<ChatPreferencesViewModel> {

    @FXML
    private ComboBox<FontFamily> fontComboBox;

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
        var family = fontComboBox.getValue();
        var size = fontSizeComboBox.getValue();
        var font = Font.font(family.getFamily(), size);
        previewLabel.setFont(font);
        defaultPreviewLabel.setFont(font);
    }

    private void initFontComboBox() {
        fontComboBox.setDisable(true);
        defaultPreviewLabel.setDisable(true);
        previewLabel.setDisable(true);

        fontComboBox.setItems(viewModel.getFontFamilies());
        fontComboBox.setConverter(new FontStringConverter(fontComboBox.getItems()));
        fontComboBox.setCellFactory(param -> new FontComboBoxCell());

        viewModel.loadFontsAsync().onDone(() -> {
            fontComboBox.setDisable(false);
            defaultPreviewLabel.setDisable(false);
            previewLabel.setDisable(false);

            fontComboBox.getSelectionModel().select(viewModel.getFont());
            viewModel.fontProperty().bind(fontComboBox.valueProperty());
        });
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
