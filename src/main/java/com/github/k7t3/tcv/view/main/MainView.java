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

import java.util.function.Function;

import java.util.function.Supplier;

import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ClipPostedAppEvent;
import com.github.k7t3.tcv.app.event.LiveNotificationEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCommands;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
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
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.animation.Animation;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;


public class MainView implements FxmlView<MainViewModel>, Initializable, AutoCloseable {
    /** 起動側で接続した生成先。生成された各部品はこのViewが所有する。 */
    public record Components(Supplier<KeyBindingCombinations> keys,
                             Function<ModalPane, OpenChannelGroupCommand> groups,
                             Function<ModalPane, OpenClipCommand> clips,
                             Supplier<OpenPreferencesCommand> preferences,
                             Function<ModalPane, OpenSearchChannelCommand> search,
                             Supplier<OpenAboutCommand> about, Supplier<BrowserActions> links,
                             Supplier<TwitchChannelListView> followers, Supplier<ChatContainerView> chats) {}

    private final Components components;
    private final AppPreferences preferences;
    private final CredentialStore credentialStore;
    private TwitchChannelListView followersView;
    private ChatContainerView chatsView;
    private BasicPopup livePopup;
    private LiveStateNotificatorView liveView;
    private LiveStateNotificator liveNotificator;
    private TypeSafeNotificationObserver<LiveNotificationEvent> liveNoticeObserver;
    private TypeSafeNotificationObserver<ClipPostedAppEvent> clipObserver;
    private TypeSafeNotificationObserver<LoginEvent> loginObserver;
    private Animation clipAnimation;
    private Animation liveAnimation;
    private AuthenticationMenuItem authMenuItem;
    private LoadCredentialCommand initialAuthentication;
    private OpenSearchChannelCommand searchCommand;
    private OpenPreferencesCommand preferencesCommand;
    private OpenChannelGroupCommand groupCommand;
    private OpenClipCommand clipCommand;
    private OpenAboutCommand aboutCommand;
    private BrowserActions externalLinks;
    private KeyBindingAccelerator accelerator;
    private ChangeListener<Scene> sceneListener;
    private boolean closed;

    public MainView(AppPreferences preferences, CredentialStore credentialStore, Components components) {
        this.preferences = java.util.Objects.requireNonNull(preferences);
        this.credentialStore = java.util.Objects.requireNonNull(credentialStore);
        this.components = java.util.Objects.requireNonNull(components);
    }

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
        if (closed) return;

        loadChatContainerView();
        if (closed) return;
        loadFollowersView();
        if (closed) return;

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
        authMenuItem = new AuthenticationMenuItem(modalPane, credentialStore, rootPane, viewModel);
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
        clipObserver = e -> {
            if (closed) return;
            stopClipAnimation();
            if (closed) return;
            var animation = Animations.wobble(clipButton);
            if (closed) { animation.stop(); return; }
            clipAnimation = animation;
            animation.play();
        };
        viewModel.subscribe(ClipPostedAppEvent.class, clipObserver);

        // ログインに成功したときのハンドラ
        loginObserver = this::onLoginEvent;
        viewModel.subscribe(LoginEvent.class, loginObserver);

        // システムメニューバーを有効化すると本来メニューバーがあった位置に
        // パディングが残るため、visibleとmanagedを常に無効化することで
        // 何もなかったかのように見せかける。
        menuBar.setVisible(false);
        menuBar.setManaged(false);
        menuBar.setUseSystemMenuBar(true);

        initLiveNotificator();
        if (closed) return;

        initCommands();
    }

    private void initCommands() {
        var commands = new KeyBindingCommands();

        var authCondition = viewModel.authorizedProperty();

        // チャンネルグループを開くコマンド
        var openChannelGroupCommand = components.groups().apply(modalPane);
        groupCommand = openChannelGroupCommand;
        if (closed) { openChannelGroupCommand.close(); return; }
        commands.updateCommand(KeyBinding.OPEN_GROUPS_VIEW, openChannelGroupCommand);

        // クリップ一覧を開くコマンド
        var openClipCommand = components.clips().apply(modalPane);
        clipCommand = openClipCommand;
        if (closed) { openClipCommand.close(); return; }
        commands.updateCommand(KeyBinding.OPEN_CLIPS_VIEW, openClipCommand);

        // 設定を開くコマンド
        var openPrefCommand = components.preferences().get();
        preferencesCommand = openPrefCommand;
        if (closed) { openPrefCommand.close(); return; }
        commands.updateCommand(KeyBinding.OPEN_PREFERENCES, openPrefCommand);

        // 検索画面を開くコマンド
        var openSearchCommand = components.search().apply(modalPane);
        searchCommand = openSearchCommand;
        if (closed) { openSearchCommand.close(); return; }
        commands.updateCommand(KeyBinding.OPEN_SEARCH_VIEW, openSearchCommand);

        // チャンネルリストの表示をトグルするコマンド
        var toggleChannelsCommand = new ToggleCommand(followerToggle.selectedProperty(), authCondition);
        commands.updateCommand(KeyBinding.TOGGLE_CHANNEL_LIST, toggleChannelsCommand);

        // Twitchの利用規約、コミュニティガイドライン
        // これらはショートカットに登録しない
        externalLinks = components.links().get();
        if (closed) { externalLinks.close(); return; }
        var openTermsCommand = new OpenTermsCommand(externalLinks);
        var openCommunityGuidelineCommand = new OpenCommunityGuidelineCommand(externalLinks);

        // このアプリケーションについてウインドウを開くコマンド
        var openAboutCommand = components.about().get();
        aboutCommand = openAboutCommand;
        if (closed) { openAboutCommand.close(); return; }

        // コマンド実行ハンドラを登録
        var keys = components.keys().get();
        if (closed) return;
        accelerator = new KeyBindingAccelerator(keys, commands);
        sceneListener = (ob, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventHandler(KeyEvent.KEY_RELEASED, accelerator);
            if (newScene != null) newScene.addEventHandler(KeyEvent.KEY_RELEASED, accelerator);
        };
        rootPane.sceneProperty().addListener(sceneListener);

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
        var combinations = components.keys().get();
        if (closed) return;
        var keyBinds = preferences.getKeyBindingPreferences();
        for (var binding : KeyBinding.values()) {
            var stored = keyBinds.readCustomCombination(binding);
            if (stored != KeyCombination.NO_MATCH)
                combinations.updateCombination(binding, stored);
        }
    }

    private void loadFollowersView() {
        var channelsViewModel = viewModel.getChannelListViewModel();

        followersView = components.followers().get();
        if (closed) { followersView.close(); return; }
        var tuple = FluentViewLoader.fxmlView(TwitchChannelListView.class).codeBehind(followersView)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(channelsViewModel)
                .load();

        if (!closed) followersContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var chatContainerViewModel = viewModel.getChatContainer();

        chatsView = components.chats().get();
        if (closed) { chatsView.close(); return; }
        var tuple = FluentViewLoader.fxmlView(ChatContainerView.class).codeBehind(chatsView)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(chatContainerViewModel)
                .load();

        if (!closed) chatContainer.getChildren().add(tuple.getView());
    }

    private void initLiveNotificator() {
        var notificator = viewModel.getLiveNotificator();

        liveNotificator = notificator;
        var controller = new LiveStateNotificatorView(notificator);
        liveView = controller;
        var converter = controller.getConverter();

        controller.setPrefWidth(340);
        controller.setPrefHeight(160);

        liveNoticeObserver = e -> {
            if (closed) return;
            var record = e.getRecord();
            liveStateLink.setText(converter.toString(record));
            liveStateLink.setVisited(false);
            if (record.live()) {
                stopLiveAnimation();
                if (closed) return;
                var animation = Animations.shakeX(liveStateLink);
                if (closed) { animation.stop(); return; }
                liveAnimation = animation;
                animation.playFromStart();
            }
        };
        notificator.subscribe(LiveNotificationEvent.class, liveNoticeObserver);

        var popup = new BasicPopup(controller);
        livePopup = popup;
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
        if (closed) return;
        if (initialAuthentication == null) {
            initialAuthentication = new LoadCredentialCommand(modalPane, viewModel.authorizedProperty().not(),
                    () -> viewModel.createAuthenticator(credentialStore));
            if (closed) { initialAuthentication.close(); return; }
        }
        initialAuthentication.execute();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(
                () -> { if (viewModel != null && clipObserver != null) viewModel.unsubscribe(ClipPostedAppEvent.class, clipObserver); },
                () -> { if (viewModel != null && loginObserver != null) viewModel.unsubscribe(LoginEvent.class, loginObserver); },
                this::stopClipAnimation, this::stopLiveAnimation,
                () -> { if (initialAuthentication != null) initialAuthentication.close(); },
                () -> { if (authMenuItem != null) authMenuItem.close(); },
                () -> { if (preferencesCommand != null) preferencesCommand.close(); },
                () -> { if (aboutCommand != null) aboutCommand.close(); },
                () -> { if (externalLinks != null) externalLinks.close(); },
                () -> { if (searchCommand != null) searchCommand.close(); },
                () -> { if (groupCommand != null) groupCommand.close(); },
                () -> { if (clipCommand != null) clipCommand.close(); },
                () -> { if (liveNotificator != null && liveNoticeObserver != null)
                    liveNotificator.unsubscribe(LiveNotificationEvent.class, liveNoticeObserver); },
                () -> { if (liveStateLink != null) liveStateLink.setOnAction(null); },
                () -> { if (liveView != null) liveView.close(); },
                () -> { if (livePopup != null) {
                    livePopup.hide(); livePopup.setOnShowing(null); livePopup.setContent(null); livePopup.setSkin(null);
                } },
                () -> { if (followersView != null) followersView.close(); },
                () -> { if (chatsView != null) chatsView.close(); },
                () -> {
                    if (rootPane != null && sceneListener != null) {
                        rootPane.sceneProperty().removeListener(sceneListener);
                        var scene = rootPane.getScene();
                        if (scene != null) scene.removeEventHandler(KeyEvent.KEY_RELEASED, accelerator);
                    }
                },
                () -> {
                    if (followerToggle != null) followerToggle.selectedProperty()
                            .unbindBidirectional(preferences.getStatePreferences().toggleChannelsProperty());
                }, this::detachControls);
    }

    private void stopClipAnimation() {
        var previous = clipAnimation; clipAnimation = null;
        if (previous != null) previous.stop();
    }

    private void stopLiveAnimation() {
        var previous = liveAnimation; liveAnimation = null;
        if (previous != null) previous.stop();
    }

    private void detachControls() {
        if (userNameLabel != null) userNameLabel.textProperty().unbind();
        if (footerLabel != null) footerLabel.textProperty().unbind();
        if (followersContainer != null) {
            followersContainer.visibleProperty().unbind();
            followersContainer.managedProperty().unbind();
            followersContainer.disableProperty().unbind();
        }
        if (chatContainer != null) chatContainer.disableProperty().unbind();
        if (clipButton != null) clipButton.visibleProperty().unbind();
        for (var button : new ButtonBase[] {groupCallerButton, clipButton, searchChannelButton, followerToggle}) {
            if (button != null) { button.disableProperty().unbind(); button.setOnAction(null); }
        }
        for (var item : new MenuItem[] {prefsMenuItem, mbPrefsMenuItem, termsMenuItem, mbTermsMenuItem,
                guidelineMenuItem, mbGuidelineMenuItem, aboutMenuItem, mbAboutMenuItem}) {
            if (item != null) { item.disableProperty().unbind(); item.setOnAction(null); }
        }
        if (userMenuButton != null) userMenuButton.hide();
        if (menuBar != null) menuBar.getMenus().forEach(Menu::hide);
        if (modalPane != null) modalPane.hide(true);
    }

    private void onLoginEvent(LoginEvent loginEvent) {
        // ModalPaneを非表示にする
        if (!closed) modalPane.hide(true);
    }

}
