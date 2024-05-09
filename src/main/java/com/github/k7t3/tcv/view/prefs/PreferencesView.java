package com.github.k7t3.tcv.view.prefs;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.prefs.*;
import com.github.k7t3.tcv.app.core.Resources;
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

import java.net.URL;
import java.util.ResourceBundle;

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

    private GeneralPreferencesViewModel generalViewModel;

    private ChatPreferencesViewModel chatViewModel;

    private ChatMessageFilterViewModel filterViewModel;

    private UserChatMessageFilterViewModel userFilterViewModel;

    private ModalPane modalPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadGeneralViewModel();
        loadChatViewModel();
        loadFilterViewModel();
        loadUserFilterViewModel();
        initButtons();
    }

    public void setModalPane(ModalPane modalPane) {
        this.modalPane = modalPane;
        root.prefWidthProperty().bind(modalPane.widthProperty().multiply(0.5));
        root.prefHeightProperty().bind(modalPane.heightProperty().multiply(0.5));
    }

    private void loadGeneralViewModel() {
        var prefs = AppPreferences.getInstance();
        generalViewModel = new GeneralPreferencesViewModel(prefs.getGeneralPreferences());

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
    }

    private void loadChatViewModel() {
        chatViewModel = new ChatPreferencesViewModel();

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

    private void loadUserFilterViewModel() {
        userFilterViewModel = new UserChatMessageFilterViewModel();

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
    }

    private void initButtons() {
        ButtonBar.setButtonData(enterButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonBar.setButtonData(resetButton, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(exportButton, ButtonBar.ButtonData.HELP_2);

        exportButton.setVisible(false);
        resetButton.setVisible(false);

        cancelButton.setOnAction(e -> {
            ThemeManager.getInstance().setTheme(generalViewModel.getDefaultTheme());
            modalPane.hide();
        });
        enterButton.setOnAction(e -> save());
    }

    private void save() {
        generalViewModel.sync();

        chatViewModel.sync();

        filterViewModel.sync();

        userFilterViewModel.sync();

        viewModel.saveAsync();
        modalPane.hide();
    }

}
