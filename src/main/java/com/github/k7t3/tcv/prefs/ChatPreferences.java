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

package com.github.k7t3.tcv.prefs;

import javafx.scene.text.Font;
import javafx.beans.property.*;

import java.util.Map;
import java.util.Optional;
import java.util.Base64;
import java.util.prefs.Preferences;

public class ChatPreferences extends PreferencesBase {

    private static final PreferenceKey<FontSettings> CHAT_FONT = new PreferenceKey<>("chat.font.family",
            () -> new FontSettings(Font.getDefault().getFamily(), Font.getDefault().getSize()),
            value -> FontSettings.deserialize(Base64.getDecoder().decode(value)),
            value -> Base64.getEncoder().encodeToString(value.serialize()));
    private static final PreferenceKey<Boolean> CHAT_SHOW_USERNAME = PreferenceKey.bool("chat.show.username", true);
    private static final PreferenceKey<Boolean> CHAT_SHOW_BADGES = PreferenceKey.bool("chat.show.badges", true);
    private static final PreferenceKey<Double> FLOATABLE_CHAT_OPACITY = PreferenceKey.decimal("floatable.chat.opacity", 0.7,
            value -> Double.isFinite(value) && value >= 0.1 && value <= 1);
    private static final PreferenceKey<Boolean> FLOATABLE_CHAT_TOP = PreferenceKey.bool("floatable.chat.always.top", true);
    private static final PreferenceKey<Integer> CHAT_CACHE_SIZE = PreferenceKey.integer("chat.cache.size", 256, value -> value > 0);

    static Map<String, PreferenceKey<?>> editorSettings() {
        return Map.of(CHAT_FONT.name(), CHAT_FONT, CHAT_SHOW_USERNAME.name(), CHAT_SHOW_USERNAME,
                CHAT_SHOW_BADGES.name(), CHAT_SHOW_BADGES, CHAT_CACHE_SIZE.name(), CHAT_CACHE_SIZE);
    }

    static java.util.Set<String> storedKeys() {
        var keys = new java.util.HashSet<>(editorSettings().keySet());
        keys.add(FLOATABLE_CHAT_OPACITY.name());
        keys.add(FLOATABLE_CHAT_TOP.name());
        return java.util.Set.copyOf(keys);
    }

    public static Map<String, Optional<String>> editorValues(FontSettings font, boolean names, boolean badges, int cache) {
        return Map.of(CHAT_FONT.name(), Optional.of(CHAT_FONT.encode(font)),
                CHAT_SHOW_USERNAME.name(), Optional.of(CHAT_SHOW_USERNAME.encode(names)),
                CHAT_SHOW_BADGES.name(), Optional.of(CHAT_SHOW_BADGES.encode(badges)),
                CHAT_CACHE_SIZE.name(), Optional.of(CHAT_CACHE_SIZE.encode(cache)));
    }

    private ObjectProperty<FontSettings> font;

    private BooleanProperty showUserName;

    private BooleanProperty showBadges;

    private DoubleProperty floatingChatOpacity;

    private BooleanProperty floatingChatAlwaysTop;

    private IntegerProperty chatCacheSize;

    ChatPreferences(Preferences preferences) { super(preferences, editorSettings().keySet()); }
    ChatPreferences(PreferenceStore store) { super(store, editorSettings().keySet()); }

    // ******************** PROPERTIES ********************

    public ObjectProperty<FontSettings> fontProperty() {
        if (font == null) {
            font = createObjectProperty(CHAT_FONT);
        }
        return font;
    }
    public FontSettings getFont() { return fontProperty().get(); }
    public void setFont(FontSettings font) { this.fontProperty().set(font); }

    public BooleanProperty showUserNameProperty() {
        if (showUserName == null) showUserName = createBooleanProperty(CHAT_SHOW_USERNAME);
        return showUserName;
    }
    public boolean isShowUserName() { return showUserNameProperty().get(); }
    public void setShowUserName(boolean showUserName) { this.showUserNameProperty().set(showUserName); }

    public BooleanProperty showBadgesProperty() {
        if (showBadges == null) showBadges = createBooleanProperty(CHAT_SHOW_BADGES);
        return showBadges;
    }
    public boolean isShowBadges() { return showBadgesProperty().get(); }
    public void setShowBadges(boolean showBadges) { this.showBadgesProperty().set(showBadges); }

    public DoubleProperty floatingChatOpacityProperty() {
        if (floatingChatOpacity == null) floatingChatOpacity = createDoubleProperty(FLOATABLE_CHAT_OPACITY);
        return floatingChatOpacity;
    }
    public double getFloatingChatOpacity() { return floatingChatOpacityProperty().get(); }
    public void setFloatingChatOpacity(double floatingChatOpacity) { floatingChatOpacityProperty().set(floatingChatOpacity); }

    public BooleanProperty floatingChatAlwaysTopProperty() {
        if (floatingChatAlwaysTop == null) floatingChatAlwaysTop = createBooleanProperty(FLOATABLE_CHAT_TOP);
        return floatingChatAlwaysTop;
    }
    public boolean getFloatingChatAlwaysTop() { return floatingChatAlwaysTopProperty().get(); }
    public void setFloatingChatAlwaysTop(boolean floatingChatAlwaysTop) { floatingChatAlwaysTopProperty().set(floatingChatAlwaysTop); }

    public IntegerProperty chatCacheSizeProperty() {
        if (chatCacheSize == null) chatCacheSize = createIntegerProperty(CHAT_CACHE_SIZE);
        return chatCacheSize;
    }
    public int getChatCacheSize() { return chatCacheSizeProperty().get(); }
    public void setChatCacheSize(int chatCacheSize) { chatCacheSizeProperty().set(chatCacheSize); }

}
