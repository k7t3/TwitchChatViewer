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
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.view.chat.ChatFont;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;

import java.util.List;
import java.util.stream.IntStream;

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

    private final ChatPreferences prefs;

    public ChatPreferencesViewModel() {
        prefs = AppPreferences.getInstance().getChatPreferences();
        showUserName = new SimpleBooleanProperty(prefs.isShowUserName());
        showBadges = new SimpleBooleanProperty(prefs.isShowBadges());
        chatCacheSize = new SimpleIntegerProperty(prefs.getChatCacheSize());

        var font = prefs.getFont();
        this.font = new SimpleObjectProperty<>(new FontFamily(font.getFamily()));
        this.fontSize = new SimpleDoubleProperty(font.getSize());
    }

    public FXTask<Void> loadFontsAsync() {
        final var partSize = 100;
        return FXTask.task(() -> {
            var families = Font.getFamilies();
            var familySize = families.size();
            var div = families.size() / partSize;
            var times = families.size() % partSize == 0 ? div : div + 1;

            IntStream.range(0, times)
                    .mapToObj(i -> families.subList(i * partSize, Math.min(familySize, (i + 1) * partSize)))
                    .map(chunk -> chunk.stream().map(FontFamily::new).toList())
                    .forEach(ff -> Platform.runLater(() -> fontFamilies.addAll(ff)));
        }).runAsync();
    }

    public ObservableList<FontFamily> getFontFamilies() {
        return fontFamilies;
    }

    @Override
    public boolean canSync() {
        return true;
    }

    @Override
    public void sync() {
        var font = getFont();
        var size = getFontSize();

        var currentFont = prefs.getFont();
        if (!font.getFamily().equals(currentFont.getFamily())
                || size != currentFont.getSize()) {
            prefs.setFont(new ChatFont(font.getFamily(), size));
        }

        prefs.setShowUserName(isShowUserName());
        prefs.setShowBadges(isShowBadges());
        prefs.setChatCacheSize(getChatCacheSize());
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
