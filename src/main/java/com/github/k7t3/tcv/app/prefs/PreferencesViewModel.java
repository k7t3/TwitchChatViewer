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

package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import de.saxsys.mvvmfx.ViewModel;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.slf4j.LoggerFactory;

public class PreferencesViewModel implements ViewModel, AutoCloseable {
    private final SettingsSaveService saves;
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Consumer<Throwable> failureHandler;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper savePending = new ReadOnlyBooleanWrapper();
    private final Object lifecycle = new Object();
    private Save activeSave;
    private boolean closed;

    private static final class Save {
        final CompletableFuture<Void> settled = new CompletableFuture<>();
        Throwable failure;
        boolean ioFinished;
        boolean errorObserved;
    }

    /**
     * 保存の実完了と未通知の失敗を、実行開始前に終了所有者へ登録する。executorは借用する。
     * UI所有者はこのモデルをcloseしてからアプリの終了を待つ。
     * 開いている画面へ引き渡した保存失敗は、終了時には再送出しない。
     */
    public PreferencesViewModel(SettingsSaveService saves, Executor executor, Consumer<CompletionStage<Void>> shutdownAwaiter,
                                Consumer<Throwable> failureHandler) {
        this.saves = Objects.requireNonNull(saves);
        this.executor = Objects.requireNonNull(executor);
        this.shutdownAwaiter = Objects.requireNonNull(shutdownAwaiter);
        this.failureHandler = Objects.requireNonNull(failureHandler);
    }

    /** 受付済み保存とFX上の適用を継続する。返す観測Taskの取消や画面終了で保存を取り消さない。 */
    public FXTask<SettingsSaveService.SavedSettings> saveAsync(SettingsDraft draft, Consumer<SettingsSaveService.SavedSettings> apply) {
        if (closed) throw new IllegalStateException("preferences editor is closed");
        if (isBusy()) throw new IllegalStateException("preferences save is already running");
        Objects.requireNonNull(draft);
        Objects.requireNonNull(apply);
        busy.set(true);
        savePending.set(true);
        var save = new Save();
        synchronized (lifecycle) { activeSave = save; }
        var task = FXTask.task(() -> {
            try { return saves.save(draft); }
            catch (RuntimeException | Error failure) {
                synchronized (lifecycle) { save.failure = failure; }
                throw failure;
            } finally {
                synchronized (lifecycle) {
                    save.ioFinished = true;
                    settle(save);
                }
            }
        });
        var applicationFailure = new AtomicReference<Throwable>();
        var observation = FXTask.<SettingsSaveService.SavedSettings>observation(() -> {
            var failure = applicationFailure.get();
            if (failure == null) failure = task.getException();
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            return task.getValue();
        });
        task.onDone(saved -> {
            try { apply.accept(saved); }
            catch (RuntimeException | Error failure) {
                applicationFailure.set(failure);
                reportFailure(failure);
            } finally { finishSave(observation); }
        });
        task.onFailure(failure -> {
            synchronized (lifecycle) {
                if (!closed) save.errorObserved = true;
                settle(save);
            }
            try { reportFailure(failure); }
            finally { finishSave(observation); }
        });
        try {
            shutdownAwaiter.accept(save.settled.minimalCompletionStage());
            executor.execute(task);
        }
        catch (RuntimeException | Error rejected) {
            synchronized (lifecycle) {
                save.ioFinished = true;
                settle(save);
            }
            task.cancel();
            observation.cancel(false);
            busy.set(false);
            savePending.set(false);
            throw rejected;
        }
        return observation;
    }

    private void reportFailure(Throwable failure) {
        if (closed) LoggerFactory.getLogger(PreferencesViewModel.class).error("preferences save failed after editor closed", failure);
        else failureHandler.accept(failure);
    }

    private void finishSave(FXTask<?> observation) {
        CloseActions.runAll(observation::run, () -> busy.set(false), () -> savePending.set(false));
    }

    /** lifecycle内で、画面へ通知済みの失敗と終了側が回収する未通知の失敗を分ける。 */
    private void settle(Save save) {
        if (!save.ioFinished) return;
        if (save.failure == null || save.errorObserved) save.settled.complete(null);
        else if (closed) save.settled.completeExceptionally(save.failure);
    }

    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
    public boolean isBusy() { return busy.get(); }
    public ReadOnlyBooleanProperty savePendingProperty() { return savePending.getReadOnlyProperty(); }
    public boolean isSavePending() { return savePending.get(); }

    /** 受付済みの保存 I/O は借用 executor が所有して完了させる。 */
    @Override public void close() {
        synchronized (lifecycle) {
            closed = true;
            if (activeSave != null) settle(activeSave);
        }
        busy.set(false);
    }
}
