package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.WorkerStateEvent;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class AuthenticationMenuItem extends MenuItem {

    private static final String STYLE_CLASS = "auth-menu-item";

    private final ModalPane modalPane;
    private final CredentialStore credentialStore;
    private final Pane rootPane;
    private final MainViewModel viewModel;
    private final BooleanProperty authorized = new SimpleBooleanProperty();

    public AuthenticationMenuItem(
            ModalPane modalPane,
            CredentialStore credentialStore,
            Pane rootPane,
            MainViewModel mainViewModel
    ) {
        this.modalPane = modalPane;
        this.credentialStore = credentialStore;
        this.rootPane = rootPane;
        this.viewModel = mainViewModel;
        getStyleClass().add(STYLE_CLASS);

        // アイコン
        var loginIcon = new FontIcon(Feather.LOG_IN);
        var logoutIcon = new FontIcon(Feather.LOG_OUT);
        graphicProperty().bind(authorized.map(b -> b ? logoutIcon : loginIcon));

        // テキスト
        var loginMsg = Resources.getString("main.menu.login");
        var logoutMsg = Resources.getString("main.menu.logout");
        textProperty().bind(authorized.map(b -> b ? logoutMsg : loginMsg));

        setOnAction(e -> {
            if (isAuthorized())
                logout();
            else
                login();
        });
    }

    private void login() {
        var authenticator = new AuthenticatorViewModel(credentialStore);
        var tuple = FluentViewLoader.fxmlView(AuthenticatorView.class)
                .viewModel(authenticator)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();

        // 読み込み画面を表示
        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(true);
        modalPane.show(view);

        // 既存の資格情報を読み込む
        var loadAsync = authenticator.loadClientAsync();
        loadAsync.onDone(() -> {
            // 資格情報の取得に成功
            if (loadAsync.getValue().isPresent()) {
                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証フローの開始
            var flowAsync = authenticator.startAuthenticateAsync();
            flowAsync.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e2 -> {
                var exception = flowAsync.getException();
                ExceptionHandler.handle(exception);
            });
        });
    }

    private void logout() {
        var parent = rootPane.getScene().getWindow();

        var confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(parent);
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(Resources.getString("logout.confirm.header"));
        confirm.setContentText(Resources.getString("logout.confirm.content"));

        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        viewModel.logout();
    }

    public BooleanProperty authorizedProperty() { return authorized; }
    public boolean isAuthorized() { return authorized.get(); }
}
