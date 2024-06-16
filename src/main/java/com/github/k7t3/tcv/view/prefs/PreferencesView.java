package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.prefs.*;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.ThemeManager;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.*;

public class PreferencesView implements FxmlView<PreferencesViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private TabPane tabPane;

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

    private AppPreferences preferences;
    private GeneralPreferencesViewModel generalViewModel;
    private List<PreferencesViewModelBase> viewModels;
    private Map<PreferencesViewModelBase, Tab> tabMap;

    private Stage preferencesStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModels = new ArrayList<>();
        tabMap = new HashMap<>();
        preferences = AppPreferences.getInstance();
        loadGeneralViewModel();
        loadChatViewModel();
        loadFilterViewModel();
        loadUserFilterViewModel();
        loadKeyBindingViewModel();

        initButtons();
    }

    public void setPreferencesStage(Stage preferencesStage) {
        this.preferencesStage = preferencesStage;
    }

    private void loadGeneralViewModel() {
        generalViewModel = new GeneralPreferencesViewModel(preferences.getGeneralPreferences());
        viewModels.add(generalViewModel);

        var tuple = FluentViewLoader.fxmlView(GeneralPreferencesView.class)
                .viewModel(generalViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var tab = new Tab(codeBehind.getName(), view);
        tab.setGraphic(codeBehind.getGraphic());
        tab.setClosable(false);
        tabPane.getTabs().addFirst(tab);
        tabMap.put(generalViewModel, tab);
    }

    private void loadChatViewModel() {
        var chatViewModel = new ChatPreferencesViewModel();
        viewModels.add(chatViewModel);

        var tuple = FluentViewLoader.fxmlView(ChatPreferencesView.class)
                .viewModel(chatViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var tab = new Tab(codeBehind.getName(), view);
        tab.setGraphic(codeBehind.getGraphic());
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
        tabMap.put(chatViewModel, tab);
    }

    private void loadFilterViewModel() {
        var filterViewModel = new ChatMessageFilterViewModel();
        viewModels.add(filterViewModel);

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
        tabMap.put(filterViewModel, tab);
    }

    private void loadUserFilterViewModel() {
        var userFilterViewModel = new UserChatMessageFilterViewModel();
        viewModels.add(userFilterViewModel);

        var tuple = FluentViewLoader.fxmlView(UserChatMessageFilterView.class)
                .viewModel(userFilterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var tab = new Tab(codeBehind.getName(), view);
        tab.setGraphic(codeBehind.getGraphic());
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
        tabMap.put(userFilterViewModel, tab);
    }

    private void loadKeyBindingViewModel() {
        var helper = AppHelper.getInstance();
        var keyBindingViewModel = new KeyBindingPreferencesViewModel(
                preferences.getKeyBindingPreferences(),
                helper.getKeyBindingCombinations()
        );
        viewModels.add(keyBindingViewModel);

        var tuple = FluentViewLoader.fxmlView(KeyBindingPreferencesView.class)
                .viewModel(keyBindingViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var tab = new Tab(codeBehind.getName(), view);
        tab.setGraphic(codeBehind.getGraphic());
        tab.setClosable(false);
        tabPane.getTabs().add(tab);
        tabMap.put(keyBindingViewModel, tab);
    }

    private void initButtons() {
        ButtonBar.setButtonData(enterButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonBar.setButtonData(resetButton, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(exportButton, ButtonBar.ButtonData.HELP_2);

        exportButton.setVisible(false);
        resetButton.setVisible(false);

        cancelButton.setOnAction(e -> {
            var tm = ThemeManager.getInstance();
            tm.setTheme(generalViewModel.getDefaultTheme());
            preferencesStage.close();
        });
        enterButton.setOnAction(e -> save());
    }

    private void save() {
        for (var viewModel : viewModels) {
            if (!viewModel.canSync()) {
                var tab = tabMap.get(viewModel);
                tabPane.getSelectionModel().select(tab);

                var view = tab.getContent();
                Animations.flash(view).play();
                return;
            }
        }

        viewModels.forEach(PreferencesViewModelBase::sync);
        viewModel.saveAsync();
        preferencesStage.close();
    }

}
