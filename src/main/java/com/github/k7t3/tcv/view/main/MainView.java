package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.prefs.KeyActionRepository;
import com.github.k7t3.tcv.view.action.*;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.web.BrowserController;
import com.github.k7t3.tcv.view.web.OpenCommunityGuidelineAction;
import com.github.k7t3.tcv.view.web.OpenTermsAction;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class MainView implements FxmlView<MainViewModel>, Initializable {

    @FXML
    private Pane rootPane;

    @FXML
    private ModalPane modalPane;

    @FXML
    private Label userNameLabel;

    @FXML
    private Button searchChannelButton;

    @FXML
    private Button clipButton;

    @FXML
    private Label footerLabel;

    @FXML
    private MenuButton userMenuButton;

    @FXML
    private MenuItem prefsMenuItem;

    @FXML
    private MenuItem loginMenuItem;

    @FXML
    private MenuItem logoutMenuItem;

    @FXML
    private MenuItem termsMenuItem;

    @FXML
    private MenuItem guidelineMenuItem;

    @FXML
    private Pane headerPane;

    @FXML
    private StackPane followersContainer;

    @FXML
    private SplitPane mainContainer;

    @FXML
    private StackPane chatContainer;

    @InjectViewModel
    private MainViewModel viewModel;

    private BrowserController browserController;

    private FollowChannelsViewModel channelsViewModel;

    private ChatRoomContainerViewModel chatContainerViewModel;

    private KeyActionRepository keyActionRepository;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        keyActionRepository = new KeyActionRepository();
        loadChatContainerView();
        loadFollowersView();

        var helper = AppHelper.getInstance();

        followersContainer.disableProperty().bind(helper.authorizedProperty().not());
        chatContainer.disableProperty().bind(helper.authorizedProperty().not());

        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        footerLabel.textProperty().bind(viewModel.footerProperty());
        footerLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        searchChannelButton.disableProperty().bind(helper.authorizedProperty().not());

        Runnable authorizedCallback = () -> {
            // ModalPaneを非表示にする
            modalPane.hide(true);

            // フォローしているチャンネルを初期化
            channelsViewModel.loadAsync();

            // チャットコンテナを初期化
            chatContainerViewModel.loadAsync();
        };
        loginMenuItem.visibleProperty().bind(helper.authorizedProperty().not());
        loginMenuItem.setOnAction(new AuthorizationViewCallAction(modalPane, authorizedCallback));

        logoutMenuItem.visibleProperty().bind(helper.authorizedProperty());
        logoutMenuItem.setOnAction(new LogoutAction(rootPane));

        clipButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.SUCCESS);
        clipButton.visibleProperty().bind(
                viewModel.clipCountProperty().greaterThan(0)
                        .and(helper.authorizedProperty())
        );
        // クリップが投稿されたらアニメーションを実行するリスナ
        viewModel.clipCountProperty().addListener((ob, o, n) -> {
            if (0 < n.intValue()) {
                var animation = Animations.wobble(clipButton);
                animation.play();
            }
        });

        initKeyActions(helper);
        initMenuItems();
    }

    private void initKeyActions(AppHelper helper) {
        var searchViewCallAction = new SearchChannelViewCallAction(modalPane, chatContainerViewModel);
        searchViewCallAction.disableProperty().bind(helper.authorizedProperty());
        searchChannelButton.setOnAction(searchViewCallAction);
        keyActionRepository.addAction(searchViewCallAction);

        var prefViewCallAction = new PreferenceViewCallAction(modalPane);
        prefViewCallAction.disableProperty().bind(modalPane.displayProperty());
        prefsMenuItem.setOnAction(prefViewCallAction);
        prefsMenuItem.acceleratorProperty().bind(prefViewCallAction.combinationProperty());
        prefsMenuItem.disableProperty().bind(prefViewCallAction.disableProperty());
        keyActionRepository.addAction(prefViewCallAction);

        var clipViewCallAction = new VideoClipListViewCallAction(modalPane, viewModel, getBrowserController());
        clipViewCallAction.disableProperty().bind(viewModel.clipCountProperty().lessThan(1));
        clipButton.setOnAction(clipViewCallAction);
        keyActionRepository.addAction(clipViewCallAction);
    }

    private BrowserController getBrowserController() {
        if (browserController == null) {
            browserController = new BrowserController(mainContainer);
        }
        return browserController;
    }

    private void initMenuItems() {
        guidelineMenuItem.setOnAction(new OpenCommunityGuidelineAction(getBrowserController()));
        termsMenuItem.setOnAction(new OpenTermsAction(getBrowserController()));
    }

    private void loadFollowersView() {
        var loader = FluentViewLoader.fxmlView(FollowChannelsView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();

        channelsViewModel = tuple.getViewModel();
        channelsViewModel.installMainViewModel(viewModel);
        channelsViewModel.installChatContainerViewModel(chatContainerViewModel);

        followersContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var loader = FluentViewLoader.fxmlView(ChatContainerView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();

        chatContainerViewModel = tuple.getViewModel();
        chatContainerViewModel.installMainViewModel(viewModel);

        chatContainer.getChildren().add(tuple.getView());
    }

    public void startMainView() {
        Runnable authorizedCallback = () -> {
            // ModalPaneを非表示にする
            modalPane.hide(true);

            // フォローしているチャンネルを初期化
            channelsViewModel.loadAsync();

            // チャットコンテナを初期化
            chatContainerViewModel.loadAsync();

            // キーアクションをインストール
            keyActionRepository.install(rootPane.getScene());
        };

        var action = new AuthorizationViewCallAction(modalPane, authorizedCallback);
        action.run();
    }

}
