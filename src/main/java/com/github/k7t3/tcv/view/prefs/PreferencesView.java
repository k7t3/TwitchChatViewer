package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.app.prefs.ChatMessageFilterViewModel;
import com.github.k7t3.tcv.app.prefs.PreferencesViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatFont;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.core.ThemeManager;
import com.github.k7t3.tcv.view.prefs.font.FontComboBoxCell;
import com.github.k7t3.tcv.view.prefs.font.FontStringConverter;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class PreferencesView implements FxmlView<PreferencesViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private TabPane tabPane;

    @FXML
    private ChoiceBox<Theme> themeChoiceBox;

    @FXML
    private ToggleSwitch experimentalSwitch;

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

    @FXML
    private ButtonBar buttonBar;

    @FXML
    private Button enterButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button exportButton;

    @InjectViewModel
    private PreferencesViewModel viewModel;

    private ChatMessageFilterViewModel filterViewModel;

    private ModalPane modalPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadFilterViewModel();
        initThemeComboBox();
        initFontComboBox();
        initButtons();
        initValues();
    }

    public void setModalPane(ModalPane modalPane) {
        this.modalPane = modalPane;
    }

    private void loadFilterViewModel() {
        filterViewModel = new ChatMessageFilterViewModel();

        var tuple = FluentViewLoader.fxmlView(ChatMessageFilterView.class)
                .viewModel(filterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var tab = new Tab(codeBehind.getName(), view);
        tab.setGraphic(codeBehind.getGraphic());
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
    }

    private void initThemeComboBox() {
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
        themeChoiceBox.valueProperty().addListener((ob, o, n) -> {
            ThemeManager.getInstance().setTheme(n);
        });
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
        });
        TaskWorker.getInstance().submit(fontLoader);
    }

    private void initButtons() {
        ButtonBar.setButtonData(enterButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonBar.setButtonData(resetButton, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(exportButton, ButtonBar.ButtonData.HELP);

        exportButton.setVisible(false);
        resetButton.setVisible(false);

        cancelButton.setOnAction(e -> {
            ThemeManager.getInstance().setTheme(initialTheme);
            modalPane.hide();
        });
        enterButton.setOnAction(e -> save());
    }

    private void save() {
        var prefs = AppPreferences.getInstance();
        prefs.setTheme(themeChoiceBox.getValue());
        prefs.setExperimental(experimentalSwitch.isSelected());

        var chatPrefs = prefs.getChatPreferences();
        chatPrefs.setFont(fontComboBox.getValue());
        chatPrefs.setShowUserName(showNameSwitch.isSelected());
        chatPrefs.setShowBadges(showBadgeSwitch.isSelected());

        filterViewModel.sync();

        viewModel.saveAsync();
        modalPane.hide();
    }

    private Theme initialTheme;

    private void initValues() {
        var prefs = AppPreferences.getInstance();
        initialTheme = prefs.getTheme();
        themeChoiceBox.getSelectionModel().select(initialTheme);
        experimentalSwitch.setSelected(prefs.isExperimental());

        var chatPrefs = prefs.getChatPreferences();
        fontComboBox.getSelectionModel().select(chatPrefs.getFont());
        showNameSwitch.setSelected(chatPrefs.isShowUserName());
        showBadgeSwitch.setSelected(chatPrefs.isShowBadges());

        defaultPreviewLabel.fontProperty().bind(fontComboBox.valueProperty().map(ChatFont::getFont));
        previewLabel.fontProperty().bind(fontComboBox.valueProperty().map(ChatFont::getFont));
    }

//    private void exportPreferences() {
//        var selector = new FileChooser();
//        selector.getExtensionFilters().addFirst(new FileChooser.ExtensionFilter("XML file", "*.xml"));
//        selector.setInitialFileName("twitch-chat-viewer.xml");
//
//        var window = root.getScene().getWindow();
//
//        var file = selector.showSaveDialog(window);
//        if (file == null)
//            return;
//
//        root.setDisable(true);
//        var task = viewModel.exportAsync(file.toPath());
//        FXTask.setOnFinished(task, e -> root.setDisable(false));
//    }
//
//    public void importPreferences() {
//        var selector = new FileChooser();
//        selector.getExtensionFilters().addFirst(new FileChooser.ExtensionFilter("XML file", "*.xml"));
//        selector.setInitialFileName("twitch-chat-viewer.xml");
//
//        var window = root.getScene().getWindow();
//
//        var file = selector.showOpenDialog(window);
//        if (file == null)
//            return;
//
//        root.setDisable(true);
//        var task = viewModel.importAsync(file.toPath());
//        FXTask.setOnFinished(task, e -> root.setDisable(false));
//    }
//
//    private void clearPreferences() {
//        root.setDisable(true);
//        var task = viewModel.clearAsync();
//        FXTask.setOnFinished(task, e -> root.setDisable(false));
//        FXTask.setOnSucceeded(task, e -> modalPane.hide());
//    }

}
