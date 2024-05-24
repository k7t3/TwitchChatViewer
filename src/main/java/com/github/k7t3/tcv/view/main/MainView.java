package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.Popover;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.KeyActionRepository;
import com.github.k7t3.tcv.view.action.*;
import com.github.k7t3.tcv.view.channel.FollowChannelsView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.core.ReadOnlyStringConverter;
import com.github.k7t3.tcv.view.web.BrowserController;
import com.github.k7t3.tcv.view.web.OpenCommunityGuidelineAction;
import com.github.k7t3.tcv.view.web.OpenTermsAction;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ResourceBundle;

public class MainView implements FxmlView<MainViewModel>, Initializable {

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

    private BrowserController browserController;

    private FollowChannelsViewModel channelsViewModel;

    private ChatRoomContainerViewModel chatContainerViewModel;

    private KeyActionRepository keyActionRepository;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var helper = AppHelper.getInstance();
        var prefs = AppPreferences.getInstance();

        chatContainerViewModel = new ChatRoomContainerViewModel(
                helper.getClipRepository(),
                prefs.getChatPreferences(),
                prefs.getMessageFilterPreferences()
        );
        helper.setContainerViewModel(chatContainerViewModel);

        keyActionRepository = new KeyActionRepository();
        loadChatContainerView();
        loadFollowersView();

        // フォロワービューは対応するトグルボタンが選択されているときのみ可視化
        followersContainer.visibleProperty().bind(followerToggle.selectedProperty());
        followersContainer.managedProperty().bind(followerToggle.selectedProperty());

        followersContainer.disableProperty().bind(helper.authorizedProperty().not());
        chatContainer.disableProperty().bind(helper.authorizedProperty().not());

        userNameLabel.getStyleClass().addAll(Styles.TEXT_SMALL);
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        footerLabel.textProperty().bind(viewModel.footerProperty());
        footerLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

        searchChannelButton.disableProperty().bind(helper.authorizedProperty().not());

        loginMenuItem.visibleProperty().bind(helper.authorizedProperty().not());
        loginMenuItem.setOnAction(new AuthorizationViewCallAction(modalPane, this::authorized));

        logoutMenuItem.visibleProperty().bind(helper.authorizedProperty());
        logoutMenuItem.setOnAction(new LogoutAction(rootPane));

        clipButton.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED, Styles.SUCCESS);
        clipButton.visibleProperty().bind(
                viewModel.clipCountProperty().greaterThan(0)
                        .and(helper.authorizedProperty())
        );
        // クリップが投稿されたらアニメーションを実行するリスナ
        viewModel.clipCountProperty().addListener((ob, o, n) -> {
            if (o.intValue() < n.intValue()) {
                var animation = Animations.wobble(clipButton);
                animation.play();
            }
        });

        initMenuItems();
    }

    private void initKeyActions(AppHelper helper) {
        var searchViewCallAction = new SearchChannelViewCallAction(modalPane);
        searchViewCallAction.disableProperty().bind(helper.authorizedProperty());
        searchChannelButton.setOnAction(searchViewCallAction);
        keyActionRepository.addAction(searchViewCallAction);

        var prefViewCallAction = new PreferenceViewCallAction(modalPane);
        prefViewCallAction.disableProperty().bind(modalPane.displayProperty());
        prefsMenuItem.setOnAction(prefViewCallAction);
        prefsMenuItem.acceleratorProperty().bind(prefViewCallAction.combinationProperty());
        prefsMenuItem.disableProperty().bind(prefViewCallAction.disableProperty());
        keyActionRepository.addAction(prefViewCallAction);

        var channelGroupViewCallAction = new ChannelGroupListViewCallAction(modalPane);
        channelGroupViewCallAction.disableProperty().bind(modalPane.displayProperty());
        groupCallerButton.setOnAction(channelGroupViewCallAction);
        keyActionRepository.addAction(channelGroupViewCallAction);

        var clipViewCallAction = new VideoClipListViewCallAction(modalPane, getBrowserController());
        clipViewCallAction.disableProperty().bind(viewModel.clipCountProperty().lessThan(1));
        clipButton.setOnAction(clipViewCallAction);
        keyActionRepository.addAction(clipViewCallAction);

        var closeChatRoomAction = new CloseChatRoomAction(chatContainerViewModel);
        keyActionRepository.addAction(closeChatRoomAction);
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
        var prefs = AppPreferences.getInstance();
        channelsViewModel = new FollowChannelsViewModel(prefs.getGeneralPreferences());

        var tuple = FluentViewLoader.fxmlView(FollowChannelsView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(channelsViewModel)
                .load();

        viewModel.installFollowChannelsViewModel(channelsViewModel);

        followersContainer.getChildren().add(tuple.getView());
    }

    private void loadChatContainerView() {
        var tuple = FluentViewLoader.fxmlView(ChatContainerView.class)
                .resourceBundle(Resources.getResourceBundle())
                .viewModel(chatContainerViewModel)
                .load();

        chatContainerViewModel = tuple.getViewModel();
        chatContainer.getChildren().add(tuple.getView());
    }

    public void startMainView() {
        var action = new AuthorizationViewCallAction(modalPane, this::authorized);
        action.run();
    }

    private void authorized() {
        // ModalPaneを非表示にする
        modalPane.hide(true);

        var helper = AppHelper.getInstance();

        // フォローしているチャンネルを初期化
        var channelRepository = helper.getChannelRepository();
        var channelLoadTask = channelRepository.loadAllAsync();
        // チャンネルをロードしたときのイベント
        channelLoadTask.setSucceeded(() -> {
            var followings = channelRepository.getFollowingChannels();
            channelsViewModel.setFollowChannels(followings);
            // グループはチャンネルがロードされたら有効にする
            groupCallerButton.setDisable(false);
            // キーアクション
            initKeyActions(helper);
        });

        // チャットコンテナを初期化
        chatContainerViewModel.loadAsync();

        // キーアクションをインストール
        keyActionRepository.install(rootPane.getScene());

        initLiveStateNotificator();
    }

    private void initLiveStateNotificator() {
        var helper = AppHelper.getInstance();
        var repository = helper.getChannelRepository();
        var notificator = repository.getNotificator();

        var converter = new ReadOnlyStringConverter<LiveStateNotificator.LiveStateRecord>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            @Override
            public String toString(LiveStateNotificator.LiveStateRecord record) {
                var name = record.channelName();
                var time = record.time().format(formatter);
                if (record.live()) {
                    return Resources.getString("main.live.state.online").formatted(name, time);
                } else {
                    return Resources.getString("main.live.state.offline").formatted(name, time);
                }
            }
        };

        notificator.getRecords().addListener((ListChangeListener<? super LiveStateNotificator.LiveStateRecord>) c -> {
            while (c.next() && c.wasAdded()) {
                for (var record : c.getAddedSubList()) {
                    liveStateLink.setText(converter.toString(record));
                }
                Animations.flash(liveStateLink).playFromStart();
            }
        });

        liveStateLink.getStyleClass().addAll(Styles.TEXT_SMALL);
        liveStateLink.visibleProperty().bind(Bindings.isEmpty(notificator.getRecords()).not());

        var liveStateList = new ListView<>(notificator.getRecords());
        liveStateList.setCellFactory(TextFieldListCell.forListView(converter));
        liveStateList.getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE);
        liveStateList.setPrefWidth(340);
        liveStateList.setPrefHeight(160);

        var popOver = new Popover(liveStateList);
        popOver.setArrowLocation(Popover.ArrowLocation.TOP_RIGHT);
        liveStateLink.setOnAction(e -> {
            if (popOver.isShowing()) {
                popOver.hide();
                return;
            }
            popOver.show(liveStateLink);
            liveStateList.scrollTo(notificator.getRecords().getLast());
        });
    }

}
