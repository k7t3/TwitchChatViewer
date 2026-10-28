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

package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.event.ClientLoadedEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.event.SessionInvalidatedEvent;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import com.github.k7t3.tcv.app.core.ClientSessions;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainViewModel extends AbstractViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainViewModel.class);

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
    private AuthenticatorViewModel activeAuthenticator;
    private final ClientSessions sessions;
    private final EventSubscribers source;
    private final Function<CredentialStore, AuthenticatorViewModel> authenticationFactory;
    private final Consumer<Throwable> failures;
    private final LiveStateNotificator liveNotificator;
    private long sessionGeneration;
    private long authenticationRequest;

    /** 渡された子モデルを所有する。セッションと発行元は借用し、ここでは閉じない。 */
    public MainViewModel(ClientSessions sessions, EventSubscribers source, ChannelViewModelRepository channelRepository,
                         ChatRoomContainerViewModel chatContainer, TwitchChannelListViewModel channelList,
                         PostedClipRepository clipRepository, LiveStateNotificator liveNotificator,
                         Function<CredentialStore, AuthenticatorViewModel> authenticationFactory, Consumer<Throwable> failures) {
        this.sessions = Objects.requireNonNull(sessions);
        this.source = Objects.requireNonNull(source);
        this.authenticationFactory = Objects.requireNonNull(authenticationFactory);
        this.failures = Objects.requireNonNull(failures);
        this.channelRepository = Objects.requireNonNull(channelRepository);
        this.chatContainer = Objects.requireNonNull(chatContainer);
        this.channelList = Objects.requireNonNull(channelList);
        this.clipRepository = Objects.requireNonNull(clipRepository);
        this.liveNotificator = Objects.requireNonNull(liveNotificator);
        try { initialize(); }
        catch (RuntimeException | Error failure) { closeAfterConstructionFailure(failure); throw failure; }
    }

    private void initialize() {
        setEventSubscribers(source);
        for (var child : List.of(channelRepository, chatContainer, channelList, clipRepository, liveNotificator)) {
            child.setEventSubscribers(source);
            child.delegateLifecycleToOwner();
        }

        // 選択しているチャンネルのライブ配信タイトルを正規化してフッターに表示するためのリスナ
        channelList.getSelectedChannels().addListener(this::onChanged);

        clipCount.bind(clipRepository.getCountBinding());

        // ログイン時のイベント
        subscribe(LoginEvent.class, this::onLogin);
        subscribe(SessionInvalidatedEvent.class, event -> {
            if (event.source() == sessions) detachSessionViews();
        });
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

    private void onLogin(LoginEvent event) {
        var generation = ++sessionGeneration;
        if (!isCurrentLogin(generation)) return;
        userName.bind(sessions.userNameProperty());
        if (!isCurrentLogin(generation)) return;
        authorized.bind(sessions.authorizedProperty());
        if (!isCurrentLogin(generation)) return;

        channelRepository.setRepository(sessions.getChannelRepository());
        if (!isCurrentLogin(generation)) return;
        clipRepository.setSession(sessions.getTwitch());
        if (!isCurrentLogin(generation)) return;
        for (var child : List.of(channelRepository, chatContainer, channelList, clipRepository, liveNotificator)) {
            if (!isCurrentLogin(generation)) return;
            child.subscribeEvents(source);
        }
        channelRepository.loadAllAsync().onDone(() -> {
            if (!isCurrentLogin(generation)) return;
            var channels = channelRepository.getChannels();
            // リポジトリがロードしたチャンネルをリストにバインド
            channelList.bindChannels(channels);

            // チャンネルがロードできたら完了イベントを発行
            if (isCurrentLogin(generation)) publish(new ClientLoadedEvent());
        }).onFailure(failure -> {
            if (isCurrentLogin(generation)) reportFailure(failure);
            else LOGGER.debug("discarded initial-load failure from an ended session", failure);
        });

        // チャットに必要な部品をロード
        if (isCurrentLogin(generation)) chatContainer.loadAsync();
    }

    private boolean isCurrentLogin(long generation) { return !isClosed() && sessionGeneration == generation; }

    public void logout() {
        if (isClosed()) return;
        sessions.logoutAsync(this::detachSessionViews).whenComplete((ignored, failure) -> {
            if (failure == null) return;
            var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            Platform.runLater(() -> {
                if (isClosed()) LOGGER.error("logout failed during shutdown", cause);
                else reportFailure(cause);
            });
        });
    }

    private void detachSessionViews() {
        sessionGeneration++;
        CloseActions.runAll(() -> CloseActions.runAll(
                () -> { if (activeAuthenticator != null) activeAuthenticator.onLogout(); },
                liveNotificator::onLogout, chatContainer::onLogout, channelList::onLogout, clipRepository::onLogout, channelRepository::onLogout),
                () -> publish(new LogoutEvent()));
    }

    private void reportFailure(Throwable failure) {
        failures.accept(failure);
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

    public LiveStateNotificator getLiveNotificator() { return liveNotificator; }

    private void closeAfterConstructionFailure(Throwable failure) {
        try { close(); }
        catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    /** 既存の認証画面を閉じ、Main と寿命を共有する認証モデルを作成する。FX スレッドから呼び出す。 */
    public AuthenticatorViewModel createAuthenticator(CredentialStore store) {
        if (isClosed()) throw new IllegalStateException("main view model is closed");
        var request = ++authenticationRequest;
        var previous = activeAuthenticator;
        activeAuthenticator = null;
        if (previous != null) previous.close();
        requireCurrentAuthentication(request, null);
        var created = authenticationFactory.apply(store);
        requireCurrentAuthentication(request, created);
        activeAuthenticator = created;
        return created;
    }

    private void requireCurrentAuthentication(long request, AuthenticatorViewModel created) {
        if (isClosed() || request != authenticationRequest) {
            var failure = new IllegalStateException(isClosed()
                    ? "main view model is closed" : "authentication request was superseded");
            try { if (created != null) created.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override
    protected void onLoggedOut() {
        // 終端済み Task の FX 成功・失敗イベントも、この時点で旧世代にする。
        sessionGeneration++;
        authenticationRequest++;
    }

    @Override
    protected void onClose() {
        sessionGeneration++;
        authenticationRequest++;
        var authenticator = activeAuthenticator;
        activeAuthenticator = null;
        CloseActions.runAll(() -> {
            if (authenticator != null) authenticator.close();
        }, () -> { if (liveNotificator != null) liveNotificator.close(); },
                () -> { if (chatContainer != null) chatContainer.close(); },
                () -> { if (channelList != null) channelList.close(); },
                () -> { if (clipRepository != null) clipRepository.close(); },
                () -> { if (channelRepository != null) channelRepository.close(); });
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
