package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.command.BasicCommand;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Side;

/**
 * 保存されている資格情報をロードするコマンド。
 * 保存されていない場合は認証フローを開始する。
 */
public class LoadCredentialCommand extends BasicCommand {

    private final ModalPane modalPane;
    private final CredentialStore credentialStore;

    public LoadCredentialCommand(
            ModalPane modalPane,
            ObservableValue<Boolean> executableCondition,
            CredentialStore credentialStore
    ) {
        this.modalPane = modalPane;
        this.credentialStore = credentialStore;
        executable.bind(executableCondition);
    }

    @Override
    public void execute() {
        // 実行中フラグを有効化
        running.set(true);

        var viewModel = new AuthenticatorViewModel(credentialStore);
        var tuple = FluentViewLoader.fxmlView(AuthenticatorView.class)
                .viewModel(viewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();

        // 読み込み画面を表示
        modalPane.usePredefinedTransitionFactories(Side.TOP);
        modalPane.setPersistent(true);
        modalPane.show(view);

        // 既存の資格情報を読み込む
        var loadAsync = viewModel.loadClientAsync();
        FXTask.setOnSucceeded(loadAsync, e -> {

            // 資格情報の取得に成功
            if (loadAsync.getValue().isPresent()) {
                // 実行中フラグを終了
                running.set(false);
                return;
            }

            // 認証が確認できなかったときは認証フローを開始する。
            // デバイス認証フローのリンクURLが表示され、そのリンクから
            // 認証が許可されるまでポーリングし続ける。

            // 認証フローの開始
            var flowAsync = viewModel.startAuthenticateAsync();
            flowAsync.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e2 -> {
                ExceptionHandler.handle(flowAsync.getException());
            });
            flowAsync.setFinally(() -> {
                // 実行中フラグを終了
                running.set(false);
            });

        });
    }

}
