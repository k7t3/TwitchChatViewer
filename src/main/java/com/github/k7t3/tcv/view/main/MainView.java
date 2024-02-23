package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.*;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.prefs.KeyActionRepository;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.action.SearchChannelViewCallAction;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.action.VideoClipListViewCallAction;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.action.PreferenceViewCallAction;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class MainView implements FxmlView<MainViewModel>, Initializable {

    @FXML
    private StackPane rootPane;

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
    private Pane headerPane;

    @FXML
    private StackPane followersContainer;

    @FXML
    private StackPane chatContainer;

    @InjectViewModel
    private MainViewModel viewModel;

    private FollowChannelsViewModel channelsViewModel;

    private ChatContainerViewModel chatContainerViewModel;

    private KeyActionRepository keyActionRepository;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        keyActionRepository = new KeyActionRepository();
        loadChatContainerView();
        loadFollowersView();

        followersContainer.disableProperty().bind(viewModel.authorizedProperty().not());
        chatContainer.disableProperty().bind(viewModel.authorizedProperty().not());

        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        footerLabel.textProperty().bind(viewModel.footerProperty());
        footerLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        searchChannelButton.disableProperty().bind(viewModel.authorizedProperty().not());

        loginMenuItem.visibleProperty().bind(viewModel.authorizedProperty().not());
        logoutMenuItem.visibleProperty().bind(viewModel.authorizedProperty());

        clipButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.SUCCESS);
        clipButton.visibleProperty().bind(viewModel.clipCountProperty().greaterThan(0));
        // クリップが投稿されたらアニメーションを実行するリスナ
        viewModel.clipCountProperty().addListener((ob, o, n) -> {
            if (0 < n.intValue()) {
                var animation = Animations.wobble(clipButton);
                animation.play();
            }
        });

        initKeyActions();
    }

    private void initKeyActions() {
        var searchViewCallAction = new SearchChannelViewCallAction(modalPane, chatContainerViewModel);
        searchChannelButton.setOnAction(searchViewCallAction);
        keyActionRepository.addAction(searchViewCallAction);

        var prefViewCallAction = new PreferenceViewCallAction(modalPane);
        prefsMenuItem.setOnAction(prefViewCallAction);
        prefsMenuItem.acceleratorProperty().bind(prefViewCallAction.combinationProperty());
        keyActionRepository.addAction(prefViewCallAction);

        var clipViewCallAction = new VideoClipListViewCallAction(modalPane);
        clipViewCallAction.disableProperty().bind(viewModel.clipCountProperty().lessThan(1));
        clipButton.setOnAction(clipViewCallAction);
        keyActionRepository.addAction(clipViewCallAction);
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
        var loader = FluentViewLoader.fxmlView(AuthenticatorView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        var view = tuple.getView();
        var authViewModel = tuple.getViewModel();

        // 読み込み画面を表示
        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(true);
        modalPane.show(view);

        Runnable callback = () -> {
            // ModalPaneを非表示にする
            modalPane.hide(true);

            // フォローしているチャンネルを初期化
            channelsViewModel.loadAsync();

            // チャットコンテナを初期化
            chatContainerViewModel.loadAsync();

            // キーアクションをインストール
            keyActionRepository.install(rootPane.getScene());
        };

        // 既存の資格情報を読み込む
        var loadAsync = authViewModel.loadClientAsync();
        FXTask.setOnSucceeded(loadAsync, e -> {

            // 資格情報の取得に成功
            if (loadAsync.getValue().isPresent()) {
                callback.run();
                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証が成功したときに処理を行うリスナーを登録
            authViewModel.authorizedProperty().addListener((ob, o, n) -> {
                if (n) {
                    callback.run();
                }
            });

            // 認証フローの開始
            var flowAsync = authViewModel.startAuthenticateAsync();
            flowAsync.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e2 -> {
                ExceptionHandler.handle(flowAsync.getException());
            });

        });
    }

}
