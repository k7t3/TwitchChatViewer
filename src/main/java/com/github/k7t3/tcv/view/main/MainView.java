/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ClipPostedAppEvent;
import com.github.k7t3.tcv.app.event.LiveNotificationEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCommands;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.channel.LiveStateNotificatorView;
import com.github.k7t3.tcv.view.channel.TwitchChannelListView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.command.*;
import com.github.k7t3.tcv.view.core.BasicPopup;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.keyboard.KeyBindingAccelerator;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class MainView implements FxmlView<MainViewModel>, Initializable {

    @FXML
    private MenuBar menuBar;

    @FXML
    private MenuItem mbPrefsMenuItem;

    @FXML
    private MenuItem mbTermsMenuItem;

    @FXML
    private MenuItem mbGuidelineMenuItem;

    @FXML
    private MenuItem mbAboutMenuItem;

    @FXML
    private Pane rootPane;

    @FXML
    private ModalPane modalPane;

    @FXML
    private Hyperlink liveStateLink;

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
    private MenuItem termsMenuItem;

    @FXML
    private MenuItem guidelineMenuItem;

    @FXML
    private MenuItem aboutMenuItem;

    @FXML
    private Pane headerPane;

    @FXML
    private ToggleButton followerToggle;

    @FXML
    private Button groupCallerButton;

    @FXML
    private StackPane followersContainer;

    @FXML
    private StackPane groupContainer;

    @FXML
    private SplitPane mainContainer;

    @FXML
    private StackPane chatContainer;

    @InjectViewModel
    private MainViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var preferences = AppPreferences.getInstance();

        loadChatContainerView();
        loadFollowersView();

        // 認証されていないコンディション
        var notAuthorizedCondition = viewModel.authorizedProperty().not();

        // フォロワービューは対応するトグルボタンが選択されているときのみ可視化
        followersContainer.visibleProperty().bind(followerToggle.selectedProperty());
        followersContainer.managedProperty().bind(followersContainer.visibleProperty());
        followersContainer.disableProperty().bind(notAuthorizedCondition);
        chatContainer.disableProperty().bind(notAuthorizedCondition);

        // チャンネルリストのトグルを設定と同期
        var state = preferences.getStatePreferences();
        followerToggle.selectedProperty().bindBidirectional(state.toggleChannelsProperty());

        // ログインしているユーザー名
        userNameLabel.getStyleClass().addAll(Styles.TEXT_SMALL);
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        // 選択しているチャンネルのライブタイトル
        footerLabel.textProperty().bind(viewModel.footerProperty());
        footerLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        // 認証用メニューアイテム
        var credentialStore = new PreferencesCredentialStorage(preferences.getPreferences());
        var authMenuItem = new AuthenticationMenuItem(modalPane, credentialStore, rootPane, viewModel);
        authMenuItem.authorizedProperty().bind(viewModel.authorizedProperty());
        userMenuButton.getItems().add(authMenuItem);

        // ライブ状態が更新されたときの表示ラベル
        liveStateLink.getStyleClass().addAll(Styles.TEXT_SMALL);
        liveStateLink.setVisited(false);

        // クリップを表示するボタン
        clipButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.SUCCESS);
        clipButton.visibleProperty().bind(
                viewModel.clipCountProperty().greaterThan(0)
                        .and(viewModel.authorizedProperty())
        );

        // クリップが投稿されたらアニメーションを実行するリスナ
        viewModel.subscribe(ClipPostedAppEvent.class, e -> {
            var animation = Animations.wobble(clipButton);
            animation.play();
        });

        // ログインに成功したときのハンドラ
        viewModel.subscribe(LoginEvent.class, this::onLoginEvent);

        // システムメニューバーを有効化すると本来メニューバーがあった位置に
        // パディングが残るため、visibleとmanagedを常に無効化することで
        // 何もなかったかのように見せかける。
        menuBar.setVisible(false);
        menuBar.setManaged(false);
        menuBar.setUseSystemMenuBar(true);

        initLiveNotificator();

        initCommands();
    }

    private void initCommands() {
        var helper = AppHelper.getInstance();
        var commands = new KeyBindingCommands();

        var authCondition = viewModel.authorizedProperty();

        // チャンネルグループを開くコマンド
        var openChannelGroupCommand = new OpenChannelGroupCommand(modalPane, helper.getChannelGroupRepository(), authCondition);
        commands.updateCommand(KeyBinding.OPEN_GROUPS_VIEW, openChannelGroupCommand);

        // クリップ一覧を開くコマンド
        var clipCondition = authCondition.and(viewModel.clipCountProperty().greaterThan(0));
        var openClipCommand = new OpenClipCommand(modalPane, viewModel.getClipRepository(), clipCondition);
        commands.updateCommand(KeyBinding.OPEN_CLIPS_VIEW, openClipCommand);

        // 設定を開くコマンド
        var openPrefCommand = new OpenPreferencesCommand();
        commands.updateCommand(KeyBinding.OPEN_PREFERENCES, openPrefCommand);

        // 検索画面を開くコマンド
        var openSearchCommand = new OpenSearchChannelCommand(modalPane, viewModel.getChannelRepository(), authCondition);
        commands.updateCommand(KeyBinding.OPEN_SEARCH_VIEW, openSearchCommand);

        // チャンネルリストの表示をトグルするコマンド
        var toggleChannelsCommand = new ToggleCommand(followerToggle.selectedProperty(), authCondition);
        commands.updateCommand(KeyBinding.TOGGLE_CHANNEL_LIST, toggleChannelsCommand);

        // Twitchの利用規約、コミュニティガイドライン
        // これらはショートカットに登録しない
        var openTermsCommand = new OpenTermsCommand();
        var openCommunityGuidelineCommand = new OpenCommunityGuidelineCommand();

        // このアプリケーションについてウインドウを開くコマンド
        var openAboutCommand = new OpenAboutCommand();

        // コマンド実行ハンドラを登録
        var accelerator = new KeyBindingAccelerator(helper.getKeyBindingCombinations(), commands);
        rootPane.sceneProperty().addListener((ob, o, n) -> {
            if (n != null)
                n.addEventHandler(KeyEvent.KEY_RELEASED, accelerator);
        });

        // 各種呼び出し用コントロールにバインド
        groupCallerButton.disableProperty().bind(openChannelGroupCommand.notExecutableProperty());
        groupCallerButton.setOnAction(openChannelGroupCommand);
        clipButton.disableProperty().bind(openClipCommand.notExecutableProperty());
        clipButton.setOnAction(openClipCommand);
        prefsMenuItem.disableProperty().bind(openPrefCommand.notExecutableProperty());
        prefsMenuItem.setOnAction(openPrefCommand);
        mbPrefsMenuItem.disableProperty().bind(openPrefCommand.notExecutableProperty());
        mbPrefsMenuItem.setOnAction(openPrefCommand);
        searchChannelButton.disableProperty().bind(openSearchCommand.notExecutableProperty());
        searchChannelButton.setOnAction(openSearchCommand);
        followerToggle.disableProperty().bind(toggleChannelsCommand.notExecutableProperty());
        termsMenuItem.setOnAction(openTermsCommand);
        termsMenuItem.disableProperty().bind(openTermsCommand.notExecutableProperty());
        mbTermsMenuItem.setOnAction(openTermsCommand);
        mbTermsMenuItem.disableProperty().bind(openTermsCommand.notExecutableProperty());
        guidelineMenuItem.setOnAction(openCommunityGuidelineCommand);
        guidelineMenuItem.disableProperty().bind(openCommunityGuidelineCommand.notExecutableProperty());
        mbGuidelineMenuItem.setOnAction(openCommunityGuidelineCommand);
        mbGuidelineMenuItem.disableProperty().bind(openCommunityGuidelineCommand.notExecutableProperty());
        aboutMenuItem.disableProperty().bind(openAboutCommand.notExecutableProperty());
        aboutMenuItem.setOnAction(openAboutCommand);
        mbAboutMenuItem.disableProperty().bind(openAboutCommand.notExecutableProperty());
        mbAboutMenuItem.setOnAction(openAboutCommand);

        // カスタムされたキーバインドを復元
        var preferences = AppPreferences.getInstance();
        var combinations = helper.getKeyBindingCombinations();
        var keyBinds = preferences.getKeyBindingPreferences();
        for (var binding : KeyBinding.values()) {
            var stored = keyBinds.readCustomCombination(binding);
            if (stored != KeyCombination.NO_MATCH)
                combinations.updateCombination(binding, stored);
        }
    }

    private void loadFollowersView() {
        var channelsViewModel = viewModel.getChannelListViewModel();

        var tuple = FluentViewLoader.fxmlView(TwitchChannelListView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(channelsViewModel)
                .load();

        followersContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var chatContainerViewModel = viewModel.getChatContainer();

        var tuple = FluentViewLoader.fxmlView(ChatContainerView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(chatContainerViewModel)
                .load();

        chatContainer.getChildren().add(tuple.getView());
    }

    private void initLiveNotificator() {
        var notificator = new LiveStateNotificator(viewModel.getChannelRepository());

        var tuple = FluentViewLoader.javaView(LiveStateNotificatorView.class)
                .viewModel(notificator)
                .load();
        var controller = tuple.getCodeBehind();
        var converter = controller.getConverter();

        controller.setItems(notificator.getRecords());
        controller.setPrefWidth(340);
        controller.setPrefHeight(160);

        notificator.subscribe(LiveNotificationEvent.class, e -> {
            var record = e.getRecord();
            liveStateLink.setText(converter.toString(record));
            liveStateLink.setVisited(false);
            if (record.live()) {
                Animations.shakeX(liveStateLink).playFromStart();
            }
        });

        var popup = new BasicPopup(controller);
        popup.setAutoHide(true);
        popup.setOnShowing(e -> {
            var last = controller.getItems().size() - 1;
            if (0 <= last)
                controller.scrollTo(last);
        });
        liveStateLink.setOnAction(e -> {
            if (popup.isShowing()) {
                popup.hide();
                return;
            }
            var bounds = JavaFXHelper.computeScreenBounds(liveStateLink);
            var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
            var y = bounds.getMaxY();
            popup.show(liveStateLink, x, y);
        });
    }

    public void startMainView() {
        var preferences = AppPreferences.getInstance();
        var command = new LoadCredentialCommand(
                modalPane,
                viewModel.authorizedProperty().not(),
                new PreferencesCredentialStorage(preferences.getPreferences())
        );
        command.execute();
    }

    private void onLoginEvent(LoginEvent loginEvent) {
        // ModalPaneを非表示にする
        modalPane.hide(true);
    }

}
