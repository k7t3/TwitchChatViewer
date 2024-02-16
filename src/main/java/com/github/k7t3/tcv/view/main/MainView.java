package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
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
    private MenuButton userMenuButton;

    @FXML
    private Pane headerPane;

    @FXML
    private StackPane leftContainer;

    @FXML
    private StackPane rightContainer;

    @InjectViewModel
    private MainViewModel viewModel;

    private FollowChannelsViewModel channelsViewModel;

    private ChatContainerViewModel chatContainerViewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        leftContainer.disableProperty().bind(viewModel.authorizedProperty().not());
        rightContainer.disableProperty().bind(viewModel.authorizedProperty().not());

        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        searchChannelButton.disableProperty().bind(viewModel.authorizedProperty().not());
        searchChannelButton.setOnAction(e -> openSearchChannelView());

        loadChatContainerView();
        loadFollowersView();
    }

    private void loadFollowersView() {
        var loader = FluentViewLoader.fxmlView(FollowChannelsView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();

        channelsViewModel = tuple.getViewModel();
        channelsViewModel.setChatContainerViewModel(chatContainerViewModel);

        leftContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var loader = FluentViewLoader.fxmlView(ChatContainerView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        chatContainerViewModel = tuple.getViewModel();
        rightContainer.getChildren().add(tuple.getView());
    }

    public void loadAuthorizationView() {
        var loader = FluentViewLoader.fxmlView(AuthenticatorView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        var view = tuple.getView();
        var authViewModel = tuple.getViewModel();

        // 読み込み画面を表示
        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(true);
        modalPane.show(view);

        // 既存の資格情報を読み込む
        var loadAsync = authViewModel.loadClientAsync();
        FXTask.setOnSucceeded(loadAsync, e -> {

            // 資格情報の取得に成功
            if (loadAsync.getValue() != null) {

                // ModalPaneを非表示にする
                modalPane.hide(true);

                // フォローしているチャンネルを初期化
                channelsViewModel.loadAsync();

                // チャットコンテナを初期化
                chatContainerViewModel.loadAsync();

                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証が成功したときに処理を行うリスナーを登録
            authViewModel.authorizedProperty().addListener((ob, o, n) -> {
                if (!n) return;

                // ModalPaneを非表示にする
                modalPane.hide(true);

                // フォローしているチャンネルを初期化
                channelsViewModel.loadAsync();

                // チャットコンテナを初期化
                chatContainerViewModel.loadAsync();
            });

            // 認証フローの開始
            var flowAsync = authViewModel.startAuthenticateAsync();
            flowAsync.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e2 -> {
                ExceptionHandler.handle(flowAsync.getException());
            });

        });
    }

    private void openSearchChannelView() {
        var loader = FluentViewLoader.fxmlView(SearchChannelView.class);
        var tuple = loader.load();
        var view = tuple.getView();
        var viewModel = tuple.getViewModel();
        viewModel.setChatContainerViewModel(chatContainerViewModel);

        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(false);
        modalPane.show(view);

        tuple.getCodeBehind().getKeywordField().requestFocus();
    }

}
