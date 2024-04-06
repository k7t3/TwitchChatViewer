package com.github.k7t3.tcv.view.action;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import com.github.k7t3.tcv.app.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Side;

public class AuthorizationViewCallAction implements Action {

    private final ModalPane modalPane;

    private final Runnable authorizedCallback;

    public AuthorizationViewCallAction(ModalPane modalPane, Runnable authorizedCallback) {
        this.modalPane = modalPane;
        this.authorizedCallback = authorizedCallback;
    }

    @Override
    public void run() {
        var tuple = FluentViewLoader.fxmlView(AuthenticatorView.class)
                .resourceBundle(Resources.getResourceBundle())
                .load();

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
            if (loadAsync.getValue().isPresent()) {
                authorizedCallback.run();
                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証が成功したときに処理を行うリスナーを登録
            authViewModel.authorizedProperty().addListener((ob, o, n) -> {
                if (n) {
                    authorizedCallback.run();
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
