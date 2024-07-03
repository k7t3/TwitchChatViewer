package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.GeneralPreferencesViewModel;
import com.github.k7t3.tcv.view.core.ThemeManager;
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
        themeChoiceBox.getItems().setAll(ThemeManager.getInstance().getThemes());
        themeChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme object) {
                return object == null ? "null" : object.getName();
            }

            @Override
            public Theme fromString(String string) {
                return themeChoiceBox.getItems()
                        .stream()
                        .filter(t -> t.getName().equalsIgnoreCase(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        // プレビューするためにテーマは即反映する
        themeChoiceBox.valueProperty().addListener((ob, o, n) -> ThemeManager.getInstance().setTheme(n));
        themeChoiceBox.getSelectionModel().select(viewModel.getTheme());
        viewModel.themeProperty().bind(themeChoiceBox.valueProperty());
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
