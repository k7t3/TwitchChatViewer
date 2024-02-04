package com.github.k7t3.tcv.view.core;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.vm.channel.FollowChannelViewModel;
import com.github.k7t3.tcv.vm.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.vm.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.core.MainViewModel;
import com.github.k7t3.tcv.vm.service.FXTask;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
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
    private CustomTextField searchField;

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

    private ChatContainerViewModel chatsViewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var unauthorized = viewModel.authorizedProperty().not();
        headerPane.disableProperty().bind(unauthorized);
        splitPane.disableProperty().bind(unauthorized);

        var helper = AppHelper.getInstance();
        userNameLabel.textProperty().bind(helper.userNameProperty());

        var clearIcon = new FontIcon(Feather.X);
        clearIcon.setOnMouseClicked(e -> viewModel.setSearchWord(null));

        searchField.textProperty().bind(viewModel.searchWordProperty());
        searchField.setRight(clearIcon);
        searchField.setLeft(new FontIcon(Feather.SEARCH));

        loadFollowersView();
        loadChatContainerView();
    }

    private void loadFollowersView() {
        var loader = FluentViewLoader.fxmlView(FollowChannelsView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        channelsViewModel = tuple.getViewModel();
        leftContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var loader = FluentViewLoader.fxmlView(ChatContainerView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        chatsViewModel = tuple.getViewModel();
        rightContainer.getChildren().add(tuple.getView());
    }

    private void loadViewResources() {
        var helper = AppHelper.getInstance();

        // フォローしているチャンネルを初期化
        channelsViewModel.setChannelRepository(new ChannelRepository(helper.getTwitch()));
        channelsViewModel.loadAsync().setOnSucceeded(e -> {
            channelsViewModel.getFollowBroadcasters().stream()
                    .filter(FollowChannelViewModel::isLive)
                    .filter(v -> v.getUserLogin().equalsIgnoreCase("raderaderader"))
                    .map(FollowChannelViewModel::getChannel)
                    .findFirst()
                    .ifPresent(channel -> chatsViewModel.register(channel));
        });

        // チャットコンテナを初期化
        chatsViewModel.loadAsync();
    }

    public void loadAuthorizationView() {
        var loader = FluentViewLoader.fxmlView(AuthenticatorView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        var view = tuple.getView();
        var authViewModel = tuple.getViewModel();

        // 読み込み画面を表示
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

}
