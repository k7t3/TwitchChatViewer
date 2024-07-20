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
import java.util.concurrent.Callable;

/**
 * 非同期で遅延読み込みするイメージ
 */
public class LazyImage {

    private final ReadOnlyObjectWrapper<Image> value = new ReadOnlyObjectWrapper<>();

    public LazyImage(String url, double requestWidth, double requestHeight) {
        var task = new LoadImageTask(url, requestWidth, requestHeight);
        task.onDone(value::set).runAsync();
    }

    public LazyImage(Callable<InputStream> callable, double requestWidth, double requestHeight) {
        var task = new LoadImageTask(callable, requestWidth, requestHeight);
        task.onDone(value::set).runAsync();
    }

    public ReadOnlyObjectProperty<Image> valueProperty() { return value.getReadOnlyProperty(); }
    public Image getValue() { return value.get(); }


    private static class LoadImageTask extends FXTask<Image> {

        private final String url;
        private final Callable<InputStream> inputCallable;
        private final double requestWidth;
        private final double requestHeight;

        public LoadImageTask(String url, double requestWidth, double requestHeight) {
            this(url, null, requestWidth, requestHeight);
        }

        public LoadImageTask(Callable<InputStream> callable, double requestWidth, double requestHeight) {
            this(null, callable, requestWidth, requestHeight);
        }

        private LoadImageTask(String url, Callable<InputStream> inputCallable, double requestWidth, double requestHeight) {
            this.url = url;
            this.inputCallable = inputCallable;
            this.requestWidth = requestWidth;
            this.requestHeight = requestHeight;
        }

        @Override
        protected Image call() throws Exception {
            if (url != null) {
                return new Image(url, requestWidth, requestHeight, true, true, false);
            } else {
                return new Image(inputCallable.call(), requestWidth, requestHeight, true, true);
            }
        }

    }
}
