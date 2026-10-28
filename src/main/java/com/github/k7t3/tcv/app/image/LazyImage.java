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

package com.github.k7t3.tcv.app.image;

import com.github.k7t3.tcv.app.service.FXTask;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 非同期で遅延読み込みするイメージ。構築とプロパティの参照は FX スレッドで行う。
 * 所有者は不要になった時点で {@link #close()} を呼び、取消と遅延結果の破棄を要求する。
 */
public class LazyImage implements AutoCloseable {

    private final ReadOnlyObjectWrapper<Image> value = new ReadOnlyObjectWrapper<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Image> completion = new CompletableFuture<>();
    private final LoadImageTask task;

    /**
     * 指定したバックグラウンド executor で読み込む。executor は借用し、このインスタンスでは終了しない。
     * 提供元から受け取ったstreamは、デコードの成功・失敗によらず閉じる。
     * @param callable 閉じる責任を移譲する stream の提供元
     * @param requestWidth 要求幅。0 の場合は元画像の幅
     * @param requestHeight 要求高さ。0 の場合は元画像の高さ
     * @param executor FX スレッドとは別のスレッドで実行する executor
     */
    public LazyImage(Callable<InputStream> callable, double requestWidth, double requestHeight, Executor executor) {
        this(new LoadImageTask(callable, requestWidth, requestHeight), executor);
    }

    private LazyImage(LoadImageTask task, Executor executor) {
        this.task = task;
        Objects.requireNonNull(executor);
        task.onDone(image -> {
            if (!closed.get()) {
                value.set(image);
                completion.complete(image);
            }
        });
        task.setOnFailed(event -> {
            if (!closed.get()) completion.completeExceptionally(task.getException());
        });
        task.setOnCancelled(event -> completion.cancel(false));
        try {
            executor.execute(task);
        } catch (RejectedExecutionException failure) {
            completion.completeExceptionally(failure);
            task.cancel();
        }
    }

    /**
     * FX 上での反映後に成功し、取得の失敗または終了時の取消も通知する。
     * 返す stage から読込を変更・取消することはできない。
     * 取消の通知は close の呼び出しスレッドでも発生する。
     * @return 画像の反映を観測する stage
     */
    public CompletionStage<Image> completion() {
        return completion.minimalCompletionStage();
    }

    /** 任意のスレッドから読込の取消を要求する。繰り返し呼び出してもよく、表示済み画像は保持する。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            task.cancel();
            completion.cancel(false);
        }
    }

    public ReadOnlyObjectProperty<Image> valueProperty() { return value.getReadOnlyProperty(); }
    public Image getValue() { return value.get(); }


    private static final class LoadImageTask extends FXTask<Image> {

        private final Callable<InputStream> inputCallable;
        private final double requestWidth;
        private final double requestHeight;

        private LoadImageTask(Callable<InputStream> callable, double requestWidth, double requestHeight) {
            this.inputCallable = callable;
            this.requestWidth = requestWidth;
            this.requestHeight = requestHeight;
        }

        @Override
        protected Image call() throws Exception {
            try (var input = inputCallable.call()) {
                return decoded(new Image(input, requestWidth, requestHeight, true, true));
            }
        }

        private static Image decoded(Image image) throws Exception {
            if (image.isError()) throw image.getException();
            return image;
        }

    }
}
