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

package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.event.SessionInvalidatedEvent;
import de.saxsys.mvvmfx.MvvmFX;
import javafx.application.Platform;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.app.prefs.SettingsSaveService;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;

import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import javafx.beans.property.*;
import javafx.stage.Stage;

import java.io.Closeable;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Consumer;

public class AppHelper implements Closeable {
    private ObjectProperty<Stage> primaryStage;

    private final EventPublishers publishers;
    private final EventSubscribers subscribers;
    private final TaskWorker worker;
    private final Consumer<Throwable> failureHandler;

    private UserDataFile userDataFile;
    private ChannelGroupRepository channelGroupRepository;
    private WindowBoundsService windowBoundsService;
    private KeyBindingCombinations keyBindingCombinations;
    private ChatFilterEntityStore chatFilterStore;
    private ChatFilters chatFilters;
    private final List<CompletionStage<Void>> lifetimeReleases = new ArrayList<>();
    private final List<Throwable> lifetimeReleaseFailures = new ArrayList<>();
    private final Object lifecycleLock = new Object();
    private ImageLoader imageLoader;
    private boolean closed;
    private boolean preparingShutdown;
    private boolean uiModelsReleased;
    private final ClientSessions sessions;
    private final CompletableFuture<Void> applicationClosed = new CompletableFuture<>();
    private Thread closingThread;

    /** このアプリ専用のワーカーと発行元を新しく所有する。 */
    public AppHelper(Consumer<Throwable> failureHandler) {
        this(new TaskWorker(), new EventPublishers(), failureHandler);
    }

    /** 注入したワーカーと発行元を、このアプリケーションが終了まで所有する。 */
    public AppHelper(TaskWorker worker, EventPublishers publishers, Consumer<Throwable> failureHandler) {
        this(worker, publishers, client -> new ChannelRepository(client, publishers), failureHandler);
    }

    AppHelper(TaskWorker worker, EventPublishers publishers, Function<Twitch, ChannelRepository> channelFactory,
              Consumer<Throwable> failureHandler) {
        this.worker = Objects.requireNonNull(worker);
        this.publishers = Objects.requireNonNull(publishers);
        this.failureHandler = Objects.requireNonNull(failureHandler);
        this.subscribers = new EventSubscribers(publishers);
        this.sessions = new ClientSessions(command -> worker.getExecutor().execute(command), Platform::runLater,
                channelFactory, this::publishSessionInvalidated);
    }

    private void publishSessionInvalidated() {
        var event = new SessionInvalidatedEvent(sessions);
        MvvmFX.getNotificationCenter().publish(SessionInvalidatedEvent.class.getName(), event);
    }

    /** 現在セッションの操作・状態を借用する。終了はアプリ全体の所有者が行う。 */
    public ClientSessions getClientSessions() {
        synchronized (lifecycleLock) { checkOpen(); return sessions; }
    }

    public void setUserDataFile(UserDataFile userDataFile) {
        synchronized (lifecycleLock) {
            checkOpen();
            Objects.requireNonNull(userDataFile);
            if (this.userDataFile != null && this.userDataFile != userDataFile) {
                throw new IllegalStateException("application already owns a user database");
            }
            this.userDataFile = userDataFile;
        }
    }

    public UserDataFile getUserDataFile() {
        synchronized (lifecycleLock) { return userDataFile; }
    }

    public ChannelGroupRepository getChannelGroupRepository() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (channelGroupRepository == null) {
                var userDataFile = Objects.requireNonNull(getUserDataFile());
                var groupService = new ChannelGroupEntityService(userDataFile.getConnector());
                channelGroupRepository = new ChannelGroupRepository(groupService, worker.getExecutor(), this::awaitOnShutdown,
                        this::reportFailure);
                channelGroupRepository.setEventSubscribers(subscribers);
            }
            return channelGroupRepository;
        }
    }

    public WindowBoundsService getWindowBoundsService() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (windowBoundsService == null) {
                var userDataFile = Objects.requireNonNull(getUserDataFile());
                var entityService = new WindowBoundsEntityService(userDataFile.getConnector());
                windowBoundsService = new WindowBoundsService(entityService, worker.getExecutor(), this::awaitOnShutdown);
            }
            return windowBoundsService;
        }
    }

    public KeyBindingCombinations getKeyBindingCombinations() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (keyBindingCombinations == null) keyBindingCombinations = new KeyBindingCombinations();
            return keyBindingCombinations;
        }
    }

    public ChatFilterEntityStore getChatFilterStore() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (chatFilterStore == null) chatFilterStore = new ChatFilterEntityStore(Objects.requireNonNull(getUserDataFile()).getConnector());
            return chatFilterStore;
        }
    }

    /** 設定キャッシュを構築する前に、接続済みDBの未完了投影を背景で復旧する。 */
    public void recoverSettings(AppPreferences preferences) {
        new SettingsSaveService(preferences, getChatFilterStore()).recover();
    }

    public ChatFilters getChatFilters() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (chatFilters == null) {
                chatFilters = new ChatFilters(getChatFilterStore(), worker.getExecutor(), this::awaitOnShutdown,
                        this::reportFailure);
                chatFilters.setEventSubscribers(subscribers);
            }
            return chatFilters;
        }
    }

    private void reportFailure(Throwable failure) {
        failureHandler.accept(failure);
    }

    public EventPublishers getPublishers() {
        return publishers;
    }

    public EventSubscribers getSubscribers() {
        return subscribers;
    }

    /**
     * アプリが所有する共有画像サービスを借用する。終了は AppHelper が行う。
     * @return 共有画像サービス
     * @throws IllegalStateException アプリが終了している場合
     */
    public ImageLoader getImageLoader() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (imageLoader == null) imageLoader = new ImageLoader(worker.getExecutor());
            return imageLoader;
        }
    }

    public ChannelRepository getChannelRepository() {
        synchronized (lifecycleLock) {
            checkOpen();
            return sessions.getChannelRepository();
        }
    }

    public Executor getExecutor() {
        synchronized (lifecycleLock) {
            checkOpen();
            return worker.getExecutor();
        }
    }

    /** UI が開始前から所有する非同期解放を登録し、アプリ終了時に完了と失敗を回収する。 */
    public void awaitOnShutdown(CompletionStage<Void> release) {
        Objects.requireNonNull(release);
        synchronized (lifecycleLock) {
            checkOpen();
            if (lifetimeReleases.contains(release)) return;
            lifetimeReleases.add(release);
            release.whenComplete((ignored, failure) -> {
                synchronized (lifecycleLock) {
                    lifetimeReleases.remove(release);
                    if (failure != null) lifetimeReleaseFailures.add(unwrap(failure));
                }
            });
        }
    }

    public CompletionStage<Void> logoutAsync() {
        return logoutAsync(() -> {});
    }

    /** FX 側を切り離してから、捕捉した同じセッションを背景で終了する。観測の取消は解放に作用しない。 */
    public CompletionStage<Void> logoutAsync(Runnable detachViews) {
        synchronized (lifecycleLock) { checkOpen(); }
        return sessions.logoutAsync(detachViews);
    }

    /** FX 側の参照・購読を閉じ、新しいサービス取得を止める。UI 所有者が close の前に呼ぶ。 */
    public void prepareShutdown() {
        final ChannelGroupRepository groups;
        final ChatFilters filters;
        final WindowBoundsService bounds;
        synchronized (lifecycleLock) {
            preparingShutdown = true;
            if (uiModelsReleased) return;
            uiModelsReleased = true;
            sessions.stopAccepting();
            groups = channelGroupRepository;
            filters = chatFilters;
            bounds = windowBoundsService;
        }
        CloseActions.runAll(sessions::freezeCredentials, sessions::detachViews, () -> { if (groups != null) groups.close(); },
                () -> { if (filters != null) filters.close(); }, () -> { if (bounds != null) bounds.close(); });
    }

    @Override
    public void close() {
        final ImageLoader images;
        final List<CompletionStage<Void>> releases;
        final List<Throwable> releaseFailures;
        final boolean owner;
        synchronized (lifecycleLock) {
            if (closingThread == Thread.currentThread()) return;
            owner = !closed;
            if (owner) {
                closed = true;
                closingThread = Thread.currentThread();
                sessions.stopAccepting();
            }
            images = imageLoader;
            releases = List.copyOf(lifetimeReleases);
            releaseFailures = List.copyOf(lifetimeReleaseFailures);
        }
        if (!owner) {
            try { applicationClosed.join(); }
            catch (CompletionException failure) { rethrow(unwrap(failure)); }
            return;
        }
        try {
            CloseActions.runAll(
                    this::prepareShutdown,
                    () -> { if (images != null) images.close(); },
                    worker::close,
                    () -> CloseActions.runAll(releases.stream().map(release -> (Runnable) () -> {
                        try { release.toCompletableFuture().join(); }
                        catch (CompletionException failure) { rethrow(unwrap(failure)); }
                    }).toArray(Runnable[]::new)),
                    () -> CloseActions.runAll(releaseFailures.stream().map(failure -> (Runnable) () -> rethrow(failure)).toArray(Runnable[]::new)),
                    sessions::close,
                    publishers::close,
                    () -> { if (userDataFile != null) userDataFile.closeDatabase(); }
            );
            applicationClosed.complete(null);
        } catch (RuntimeException | Error failure) {
            applicationClosed.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (lifecycleLock) { closingThread = null; }
        }
    }


    // ########################################
    // PROPERTIES

    public ReadOnlyStringProperty userIdProperty() { return sessions.userIdProperty(); }
    public String getUserId() { return sessions.getUserId(); }

    public ReadOnlyStringProperty userNameProperty() { return sessions.userNameProperty(); }
    public String getUserName() { return sessions.getUserName(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return sessions.authorizedProperty(); }
    public boolean isAuthorized() { return sessions.isAuthorized(); }

    public ReadOnlyObjectProperty<Twitch> twitchProperty() { return sessions.twitchProperty(); }
    public Twitch getTwitch() { return sessions.getTwitch(); }
    public void setTwitch(Twitch client) {
        synchronized (lifecycleLock) { checkOpen(); }
        sessions.adopt(client);
    }

    private void checkOpen() {
        if (closed || preparingShutdown) throw new IllegalStateException("application is closed");
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException(failure);
    }

    public ObjectProperty<Stage> primaryStageProperty() {
        if (primaryStage == null) primaryStage = new SimpleObjectProperty<>();
        return primaryStage;
    }
    public Stage getPrimaryStage() { return primaryStageProperty().get(); }
    public void setPrimaryStage(Stage primaryStage) { primaryStageProperty().set(primaryStage); }

}
