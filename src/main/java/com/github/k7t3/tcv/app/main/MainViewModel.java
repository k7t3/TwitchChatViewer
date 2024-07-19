package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.event.ClientLoadedEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;

import java.util.stream.Collectors;

public class MainViewModel extends AbstractViewModel {

    private static final int NORM_STREAM_TITLE_LENGTH = 20;

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper footer = new ReadOnlyStringWrapper();
    private final ReadOnlyIntegerWrapper clipCount = new ReadOnlyIntegerWrapper();
    private final StringProperty title = new SimpleStringProperty("Twitch Chat Viewer");
    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    private final ChannelViewModelRepository channelRepository;
    private final ChatRoomContainerViewModel chatContainer;
    private final TwitchChannelListViewModel channelList;
    private final PostedClipRepository clipRepository;

    public MainViewModel(ChannelGroupRepository groupRepository) {
        channelRepository = new ChannelViewModelRepository(groupRepository);
        chatContainer = new ChatRoomContainerViewModel();
        channelList = new TwitchChannelListViewModel();
        clipRepository = new PostedClipRepository();

        // 選択しているチャンネルのライブ配信タイトルを正規化してフッターに表示するためのリスナ
        channelList.getSelectedChannels().addListener(this::onChanged);

        clipCount.bind(clipRepository.getCountBinding());

        // ログイン時のイベント
        subscribe(LoginEvent.class, this::onLogin);
    }

    // 選択しているチャンネルの配信タイトルをフッターに表示するためのリスナ
    private void onChanged(ListChangeListener.Change<? extends TwitchChannelViewModel> c) {
        var list = c.getList();
        if (list.isEmpty())
            return;

        if (list.size() == 1) {
            setFooter(list.getFirst().getStreamTitle());
            return;
        }

        var titles = list.stream()
                .map(this::normTitle)
                .collect(Collectors.joining(" / "));
        setFooter(titles);
    }

    private String normTitle(TwitchChannelViewModel channel) {
        // TODO StringUtils
        var title = channel.getStreamTitle();
        if (title.length() <= NORM_STREAM_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, NORM_STREAM_TITLE_LENGTH);
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    private void onLogin(LoginEvent event) {
        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();
        var publishers = helper.getPublishers();

        userName.bind(helper.userNameProperty());
        authorized.bind(helper.authorizedProperty());

        channelRepository.setRepository(new ChannelRepository(twitch, publishers));
        channelRepository.loadAllAsync().onDone(() -> {
            var channels = channelRepository.getChannels();
            // リポジトリがロードしたチャンネルをリストにバインド
            channelList.bindChannels(channels);

            // チャンネルがロードできたら完了イベントを発行
            publish(new ClientLoadedEvent());
        });

        // チャットに必要な部品をロード
        chatContainer.loadAsync();
    }

    public void logout() {
        var helper = AppHelper.getInstance();
        helper.logoutAsync();

        publish(new LogoutEvent());
    }

    public ChannelViewModelRepository getChannelRepository() {
        return channelRepository;
    }

    public ChatRoomContainerViewModel getChatContainer() {
        return chatContainer;
    }

    public TwitchChannelListViewModel getChannelListViewModel() {
        return channelList;
    }

    public PostedClipRepository getClipRepository() {
        return clipRepository;
    }

    @Override
    public void onLogout() {
        // ログアウトはlogout()メソッド起点で行うためここでは何もしない
    }

    @Override
    public void close() {
        // no-op
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public ReadOnlyStringProperty footerProperty() { return footer.getReadOnlyProperty(); }
    public String getFooter() { return footer.get(); }
    private void setFooter(String footer) { this.footer.set(footer); }

    public ReadOnlyIntegerProperty clipCountProperty() { return clipCount.getReadOnlyProperty(); }
    public int getClipCount() { return clipCount.get(); }
    private void setClipCount(int clipCount) { this.clipCount.set(clipCount); }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String title) { this.title.set(title); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }
}
