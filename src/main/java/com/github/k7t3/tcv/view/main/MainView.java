package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.channel.SearchChannelView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.vm.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.vm.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.core.ExceptionHandler;
import com.github.k7t3.tcv.vm.main.MainViewModel;
import com.github.k7t3.tcv.vm.service.FXTask;
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
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private SplitPane splitPane;

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
        var unauthorized = viewModel.authorizedProperty().not();
        headerPane.disableProperty().bind(unauthorized);
        //splitPane.disableProperty().bind(unauthorized);

        var helper = AppHelper.getInstance();
        userNameLabel.textProperty().bind(helper.userNameProperty());

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

    private void loadViewResources() {
        var helper = AppHelper.getInstance();

        // フォローしているチャンネルを初期化
        channelsViewModel.setChannelRepository(new ChannelRepository(helper.getTwitch()));
        channelsViewModel.loadAsync();

        // チャットコンテナを初期化
        chatContainerViewModel.loadAsync();
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
        var loadAsync = authViewModel.loadCredentialAsync();
        FXTask.setOnSucceeded(loadAsync, e -> {

            // 資格情報の取得に成功
            if (loadAsync.getValue() != null) {

                // 認証済み
                viewModel.setAuthorized(true);

                // ModalPaneを非表示にする
                modalPane.hide(true);

                // リソースのクリーンアップ
                authViewModel.close();

                loadViewResources();
                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証が成功したときに処理を行うリスナーを登録
            authViewModel.authorizedProperty().addListener((ob, o, n) -> {
                // 認証結果を反映
                viewModel.setAuthorized(n);

                if (!n) return;

                // ModalPaneを非表示にする
                modalPane.hide(true);

                // リソースのクリーンアップ
                authViewModel.close();

                loadViewResources();
            });

            // 認証フローの開始
            var flowAsync = authViewModel.startAuthenticateAsync();
            flowAsync.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e2 -> {
                ExceptionHandler.handle(flowAsync.getException());
                authViewModel.close();
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
