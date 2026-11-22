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
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.prefs.FontSettings;
import javafx.beans.property.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChatPreferencesViewModel implements PreferencesViewModelBase {

    public static final List<Integer> CHAT_CACHE_SIZES = List.of(
            32, 64, 128, 256, 512, 1024, 2048
    );

    public static final List<Double> FONT_SIZES = List.of(
            8d, 9d, 10d, 10.5, 11d, 12d, 13d, 14d, 16d, 18d
    );

    private final ObjectProperty<FontFamily> font;
    private final DoubleProperty fontSize;
    private final BooleanProperty showUserName;
    private final BooleanProperty showBadges;
    private final IntegerProperty chatCacheSize;

    private final ObservableList<FontFamily> fontFamilies = FXCollections.observableArrayList();

    private final Executor executor;
    private final Supplier<List<String>> fontLookup;
    private final Consumer<Throwable> failures;
    private FXTask<List<String>> fontLoad;
    private Object fontRequest;
    private final ReadOnlyBooleanWrapper fontsReady = new ReadOnlyBooleanWrapper();
    private boolean closed;

    public ChatPreferencesViewModel(ChatPreferences prefs, Executor executor, Consumer<Throwable> failures) {
        this(prefs, executor, Font::getFamilies, failures);
    }

    ChatPreferencesViewModel(ChatPreferences prefs, Executor executor, Supplier<List<String>> fontLookup,
                             Consumer<Throwable> failures) {
        this.executor = executor;
        this.fontLookup = fontLookup;
        this.failures = failures;
        showUserName = new SimpleBooleanProperty(prefs.isShowUserName());
        showBadges = new SimpleBooleanProperty(prefs.isShowBadges());
        chatCacheSize = new SimpleIntegerProperty(prefs.getChatCacheSize());

        var font = prefs.getFont();
        this.font = new SimpleObjectProperty<>(new FontFamily(font.family()));
        this.fontSize = new SimpleDoubleProperty(font.size());
    }

    public FXTask<List<String>> loadFontsAsync() {
        if (closed) throw new IllegalStateException("chat preferences editor is closed");
        if (fontLoad != null) return fontLoad;
        var task = FXTask.task(() -> List.copyOf(fontLookup.get()));
        fontLoad = task;
        var request = new Object();
        fontRequest = request;
        task.onDone(families -> {
            if (closed || fontRequest != request) return;
            fontFamilies.clear();
            publishFontBatch(families, 0, request);
        });
        task.onFailure(failure -> { if (!closed && fontLoad == task) failures.accept(failure); });
        task.setFinally(() -> {
            if (fontLoad == task && task.getState() != javafx.concurrent.Worker.State.SUCCEEDED) {
                fontLoad = null;
                fontRequest = null;
            }
        });
        try { executor.execute(task); }
        catch (RejectedExecutionException closing) { task.cancel(); }
        return task;
    }

    private void publishFontBatch(List<String> names, int start, Object request) {
        if (closed || fontRequest != request) return;
        var end = Math.min(names.size(), start + 100);
        fontFamilies.addAll(names.subList(start, end).stream().map(FontFamily::new).toList());
        if (closed || fontRequest != request) return;
        if (end < names.size()) Platform.runLater(() -> publishFontBatch(names, end, request));
        else fontsReady.set(true);
    }

    public ReadOnlyBooleanProperty fontsReadyProperty() { return fontsReady.getReadOnlyProperty(); }

    public ObservableList<FontFamily> getFontFamilies() {
        return fontFamilies;
    }

    @Override
    public boolean canSync() {
        return !closed && getFont() != null && getFont().getFamily() != null && !getFont().getFamily().isBlank()
                && Double.isFinite(getFontSize()) && getFontSize() > 0 && getChatCacheSize() > 0;
    }

    @Override
    public void prepare(SettingsDraft.Builder target) {
        if (!canSync()) throw new IllegalStateException("chat preferences contain an invalid font or cache size");
        target.values(ChatPreferences.editorValues(new FontSettings(getFont().getFamily(), getFontSize()),
                isShowUserName(), isShowBadges(), getChatCacheSize()));
    }

    public boolean isClosed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var pending = fontLoad;
        fontLoad = null;
        fontRequest = null;
        if (pending != null) pending.cancel();
        fontFamilies.clear();
        fontsReady.set(false);
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<FontFamily> fontProperty() { return font; }
    public FontFamily getFont() { return font.get(); }
    public void setFont(FontFamily font) { this.font.set(font); }

    public DoubleProperty fontSizeProperty() { return fontSize; }
    public double getFontSize() { return fontSize.get(); }
    public void setFontSize(double size) { fontSize.set(size); }

    public BooleanProperty showUserNameProperty() { return showUserName; }
    public boolean isShowUserName() { return showUserName.get(); }
    public void setShowUserName(boolean showUserName) { this.showUserName.set(showUserName); }

    public BooleanProperty showBadgesProperty() { return showBadges; }
    public boolean isShowBadges() { return showBadges.get(); }
    public void setShowBadges(boolean showBadges) { this.showBadges.set(showBadges); }

    public IntegerProperty chatCacheSizeProperty() { return chatCacheSize; }
    public int getChatCacheSize() { return chatCacheSize.get(); }
    public void setChatCacheSize(int chatCacheSize) { this.chatCacheSize.set(chatCacheSize); }

}
