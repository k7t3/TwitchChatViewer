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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class PreferencesView implements FxmlView<PreferencesViewModel>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private TreeView<PreferencesPage<?>> pagesTreeView;

    @FXML
    private StackPane contentPane;

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

    private TreeItem<PreferencesPage<?>> pagesRoot;

    private AppPreferences preferences;
    private GeneralPreferencesViewModel generalViewModel;
    private List<PreferencesViewModelBase> viewModels;
    private Map<PreferencesViewModelBase, TreeItem<PreferencesPage<?>>> pageMap;
    private Map<PreferencesPage<?>, Node> viewMap;

    private Stage preferencesStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModels = new ArrayList<>();
        pageMap = new HashMap<>();
        viewMap = new HashMap<>();
        pagesRoot = new TreeItem<>();
        preferences = AppPreferences.getInstance();

        pagesTreeView.setRoot(pagesRoot);
        pagesTreeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        pagesTreeView.setCellFactory(v -> new PreferencesPageTreeCell());
        pagesTreeView.getSelectionModel().selectedItemProperty().addListener((ob, o, n) -> {
            if (n == null) {
                contentPane.getChildren().clear();
            } else {
                var node = viewMap.get(n.getValue());
                contentPane.getChildren().setAll(node);
            }
        });

        loadGeneralViewModel();
        loadChatViewModel();
        loadFilterViewModel();
        loadUserFilterViewModel();
        loadKeyBindingViewModel();

        initButtons();

        pagesTreeView.getSelectionModel().select(0);
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

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(generalViewModel, item);
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

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(chatViewModel, item);
    }

    private void loadFilterViewModel() {
        var helper = AppHelper.getInstance();
        var filterViewModel = new KeywordFilterViewModel(helper.getChatFilters());
        viewModels.add(filterViewModel);

        var tuple = FluentViewLoader.fxmlView(KeywordFilterView.class)
                .viewModel(filterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(filterViewModel, item);
    }

    private void loadUserFilterViewModel() {
        var helper = AppHelper.getInstance();
        var userFilterViewModel = new UserFilterViewModel(helper.getChatFilters());
        viewModels.add(userFilterViewModel);

        var tuple = FluentViewLoader.fxmlView(UserFilterView.class)
                .viewModel(userFilterViewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(userFilterViewModel, item);
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

        var item = new TreeItem<PreferencesPage<?>>(codeBehind);
        pagesRoot.getChildren().add(item);
        viewMap.put(codeBehind, view);
        pageMap.put(keyBindingViewModel, item);
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
                var page = pageMap.get(viewModel);
                pagesTreeView.getSelectionModel().select(page);

                var view = viewMap.get(page.getValue());
                Animations.flash(view).play();
                return;
            }
        }

        viewModels.forEach(PreferencesViewModelBase::sync);
        viewModel.saveAsync();
        preferencesStage.close();
    }

}
