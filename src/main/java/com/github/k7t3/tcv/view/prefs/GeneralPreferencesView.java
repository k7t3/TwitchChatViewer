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

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.GeneralPreferencesViewModel;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.theme.ThemeType;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

public class GeneralPreferencesView implements PreferencesPage<GeneralPreferencesViewModel> {

    @FXML
    private ChoiceBox<Theme> themeChoiceBox;

    @FXML
    private ChoiceBox<ThemeType> themeTypeChoiceBox;

    @FXML
    private ComboBox<MultipleChatOpenType> openTypeComboBox;

    @InjectViewModel
    private GeneralPreferencesViewModel viewModel;

    private Map<MultipleChatOpenType, String> openTypeNames;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        openTypeNames = Map.of(
                MultipleChatOpenType.SEPARATED, Resources.getString("prefs.behavior.open.multi.separated"),
                MultipleChatOpenType.MERGED, Resources.getString("prefs.behavior.open.multi.merged")
        );

        initThemeChoiceBox();
        initOpenTypeComboBox();
    }

    private void initOpenTypeComboBox() {
        openTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MultipleChatOpenType openType) {
                return openTypeNames.get(openType);
            }

            @Override
            public MultipleChatOpenType fromString(String s) {
                return openTypeNames.entrySet().stream()
                        .filter(e -> e.getValue().equals(s))
                        .findFirst()
                        .map(Map.Entry::getKey)
                        .orElseThrow();
            }
        });
        openTypeComboBox.getItems().setAll(MultipleChatOpenType.values());
        openTypeComboBox.getSelectionModel().select(viewModel.getChatOpenType());
        viewModel.chatOpenTypeProperty().bind(openTypeComboBox.valueProperty());
    }

    private void initThemeChoiceBox() {
        themeChoiceBox.getItems().setAll(Theme.values());
        themeChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme object) {
                return object == null ? "null" : object.getThemeName();
            }

            @Override
            public Theme fromString(String string) {
                return themeChoiceBox.getItems()
                        .stream()
                        .filter(t -> t.getThemeName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        // プレビューするためにテーマは即反映する
        themeChoiceBox.valueProperty().addListener((ob, o, n) -> ThemeManager.getInstance().setTheme(n));
        themeChoiceBox.getSelectionModel().select(viewModel.getTheme());
        viewModel.themeProperty().bind(themeChoiceBox.valueProperty());

        var typeNames = Map.of(
                ThemeType.LIGHTER, Resources.getString("prefs.theme.lighter"),
                ThemeType.DARKER, Resources.getString("prefs.theme.darker"),
                ThemeType.SYSTEM, Resources.getString("prefs.theme.system")
        );
        themeTypeChoiceBox.getItems().setAll(ThemeType.values());
        themeTypeChoiceBox.setConverter(new StringConverter<ThemeType>() {
            @Override
            public String toString(ThemeType themeType) {
                return typeNames.get(themeType);
            }

            @Override
            public ThemeType fromString(String s) {
                return typeNames.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().equals(s))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElseThrow();
            }
        });
        // プレビューするためにテーマは即反映する
        themeTypeChoiceBox.valueProperty().addListener((ob, o, n) -> ThemeManager.getInstance().setThemeType(n));
        themeTypeChoiceBox.getSelectionModel().select(viewModel.getThemeType());
        viewModel.themeTypeProperty().bind(themeTypeChoiceBox.valueProperty());
    }

    @Override
    public String getName() {
        return Resources.getString("prefs.tab.general");
    }

    @Override
    public Node getGraphic() {
        return new FontIcon(Feather.SETTINGS);
    }
}
