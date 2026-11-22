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

package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.app.event.UserFilteringEvent;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;

import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.List;

public class ChatFilters extends AbstractViewModel {

    /**
     * UI のモデルを含まない、フィルターの読み込み結果。両リストは変更不可のコピーとして保持する。
     * @param keywords キーワードフィルター
     * @param users ユーザーフィルター
     */
    public record Snapshot(List<ChatKeywordFilterEntity> keywords, List<ChatUserFilterEntity> users, long revision) {
        public Snapshot(List<ChatKeywordFilterEntity> keywords, List<ChatUserFilterEntity> users) { this(keywords, users, 0); }
        public static Snapshot from(ChatFilterEntityStore.Snapshot snapshot) {
            return new Snapshot(snapshot.keywords(), snapshot.users(), snapshot.revision());
        }
        public Snapshot {
            keywords = List.copyOf(keywords);
            users = List.copyOf(users);
        }
    }

    private final ChatFilterEntityStore store;
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Consumer<Throwable> failures;
    private final Object writesLock = new Object();
    private final Set<Write> pendingWrites = new HashSet<>();
    private long appliedRevision = -1;
    private long session;

    private final ObservableList<KeywordFilterEntry> keywordEntries = FXCollections.observableArrayList();
    private final ObservableList<UserFilterEntry> userEntries = FXCollections.observableArrayList();

    private final Predicate<ChatData> filter;

    private static final class Write {
        final CompletableFuture<Void> settled = new CompletableFuture<>();
        Throwable failure;
        boolean ioFinished;
        boolean errorObserved;
        boolean retired;
    }

    /**
     * 実行前に保存の終了をshutdownAwaiterへ登録する。executorは借用する。
     * 返すTaskの取消で受付済みの書き込みを取り消さず、終了時は未通知の保存失敗を回収する。
     */
    public ChatFilters(ChatFilterEntityStore store, Executor executor, Consumer<CompletionStage<Void>> shutdownAwaiter,
                       Consumer<Throwable> failures) {
        this.store = java.util.Objects.requireNonNull(store);
        this.executor = java.util.Objects.requireNonNull(executor);
        this.shutdownAwaiter = java.util.Objects.requireNonNull(shutdownAwaiter);
        this.failures = java.util.Objects.requireNonNull(failures);
        filter = new KeywordFilter(keywordEntries).or(new UserFilter(userEntries));
    }

    public Predicate<ChatData> getFilter() {
        return filter;
    }

    public ObservableList<KeywordFilterEntry> getKeywordEntries() {
        return keywordEntries;
    }

    public ObservableList<UserFilterEntry> getUserEntries() {
        return userEntries;
    }

    /** UI 状態を変更せず永続化データを読み込む。バックグラウンドから呼び出せる。 */
    public Snapshot readSnapshot() {
        return Snapshot.from(store.read());
    }

    /**
     * 読み込み結果から UI の項目を作成し、両一覧を置き換える。変換失敗時はいずれの一覧も変更しない。
     * @param snapshot 読み込み済みのデータ
     * @throws IllegalStateException FX スレッド以外から呼び出した場合
     */
    public void applySnapshot(Snapshot snapshot) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("use the FX application thread");
        if (isClosed() || snapshot.revision() < appliedRevision) return;
        var owner = session;
        var ke = snapshot.keywords().stream().map(this::fromKeywordEntity).toList();

        var ue = snapshot.users()
                .stream()
                .map(u -> new UserFilterEntry(u.userId(), u.userName(), u.comment()))
                .toList();
        appliedRevision = snapshot.revision();
        keywordEntries.setAll(ke);
        if (isClosed() || session != owner || snapshot.revision() != appliedRevision) return;
        userEntries.setAll(ue);
    }

    /** 保存後の全体像を反映してから、既存メッセージにもフィルターを適用する。 */
    public void applyCommittedSnapshot(ChatFilterEntityStore.Snapshot saved) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("use the FX application thread");
        if (isClosed() || saved.revision() < appliedRevision) return;
        var owner = session;
        applySnapshot(Snapshot.from(saved));
        // 通知中に一覧が再入変更されても、列挙を壊さない。
        for (var entry : List.copyOf(keywordEntries)) {
            if (isClosed() || session != owner || saved.revision() != appliedRevision) return;
            publish(new KeywordFilteringEvent(entry));
        }
        for (var entry : List.copyOf(userEntries)) {
            if (isClosed() || session != owner || saved.revision() != appliedRevision) return;
            publish(new UserFilteringEvent(entry));
        }
    }

    /** 現在のセッションで受け付けた保存結果だけを反映する FX 用 callback。保存 I/O 自体は取り消さない。 */
    public Consumer<ChatFilterEntityStore.Snapshot> captureCommitHandler() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("use the FX application thread");
        var owner = session;
        return saved -> { if (!isClosed() && session == owner) applyCommittedSnapshot(saved); };
    }

    public FXTask<ChatFilterEntityStore.Snapshot> saveUserFilter(UserFilterEntry entry) {
        var row = new ChatUserFilterEntity(entry.getUserId(), entry.getUserName(), entry.getComment());
        return submit(new ChatFilterEntityStore.Changes(List.of(), Set.of(), List.of(row), Set.of()));
    }

    public FXTask<ChatFilterEntityStore.Snapshot> deleteUserFilter(UserFilterEntry entry) {
        return submit(new ChatFilterEntityStore.Changes(List.of(), Set.of(), List.of(), Set.of(entry.getUserId())));
    }

    public FXTask<ChatFilterEntityStore.Snapshot> saveKeywordFilter(KeywordFilterEntry entry) {
        var row = new ChatKeywordFilterEntity(Math.max(0, entry.getFilterId()), entry.getFilterType().getType(), entry.getKeyword());
        return submit(new ChatFilterEntityStore.Changes(List.of(row), Set.of(), List.of(), Set.of()));
    }

    public FXTask<ChatFilterEntityStore.Snapshot> deleteKeywordFilter(KeywordFilterEntry entry) {
        var deleted = entry.getFilterId() > 0 ? Set.of(entry.getFilterId()) : Set.<Integer>of();
        return submit(new ChatFilterEntityStore.Changes(List.of(), deleted, List.of(), Set.of()));
    }

    private FXTask<ChatFilterEntityStore.Snapshot> submit(ChatFilterEntityStore.Changes changes) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("use the FX application thread");
        if (isClosed()) throw new IllegalStateException("chat filters are closed");
        var owner = session;
        var write = new Write();
        synchronized (writesLock) { pendingWrites.add(write); }
        var task = FXTask.task(() -> {
            try { return store.save(changes, () -> {}); }
            catch (RuntimeException | Error failure) {
                synchronized (writesLock) { write.failure = failure; }
                throw failure;
            } finally {
                synchronized (writesLock) {
                    write.ioFinished = true;
                    settle(write);
                }
            }
        });
        var applicationFailure = new AtomicReference<Throwable>();
        var observation = FXTask.observation(() -> {
            var failure = applicationFailure.get();
            if (failure == null) failure = task.getException();
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            return task.getValue();
        });
        var committed = captureCommitHandler();
        task.onDone(saved -> {
            try { committed.accept(saved); }
            catch (RuntimeException | Error failure) {
                applicationFailure.set(failure);
                failures.accept(failure);
            }
            finally { observation.run(); }
        });
        task.onFailure(failure -> {
            var visible = !isClosed() && session == owner;
            synchronized (writesLock) {
                if (visible) write.errorObserved = true;
                settle(write);
            }
            try { if (visible) failures.accept(failure); }
            finally { observation.run(); }
        });
        try {
            shutdownAwaiter.accept(write.settled.minimalCompletionStage());
            executor.execute(task);
        } catch (RuntimeException | Error failure) {
            synchronized (writesLock) {
                write.ioFinished = true;
                settle(write);
            }
            task.cancel();
            observation.cancel();
            throw failure;
        }
        return observation;
    }

    /** writesLock内で呼び、開いた画面が扱った失敗を終了時に再送出しない。 */
    private void settle(Write write) {
        if (!write.ioFinished) return;
        if (write.failure == null || write.errorObserved) write.settled.complete(null);
        else if (isClosed() || write.retired) write.settled.completeExceptionally(write.failure);
        if (write.settled.isDone()) pendingWrites.remove(write);
    }

    private KeywordFilterEntry fromKeywordEntity(ChatKeywordFilterEntity entity) {
        var type = KeywordFilterType.of(entity.type());
        var keyword = entity.keyword();
        var entry = switch (type) {
            case CONTAINS -> KeywordFilterEntry.containsMatch(keyword);
            case PREFIX_MATCH -> KeywordFilterEntry.prefixMatch(keyword);
            case EXACT_MATCH -> KeywordFilterEntry.exactMatch(keyword);
            case REGEXP -> KeywordFilterEntry.regexMatch(Pattern.compile(keyword));
        };
        entry.setFilterId(entity.id());
        return entry;
    }

    @Override
    protected void onLoggedOut() {
        session++;
        synchronized (writesLock) {
            for (var write : List.copyOf(pendingWrites)) {
                write.retired = true;
                settle(write);
            }
        }
        keywordEntries.clear();
        userEntries.clear();
    }

    @Override
    protected void onClose() {
        synchronized (writesLock) {
            for (var write : List.copyOf(pendingWrites)) settle(write);
        }
        keywordEntries.clear();
        userEntries.clear();
    }
}
