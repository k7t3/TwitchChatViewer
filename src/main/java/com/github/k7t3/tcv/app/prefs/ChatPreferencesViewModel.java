package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.chat.ChatFont;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import javafx.beans.property.*;
import javafx.scene.text.Font;

import java.util.List;
import java.util.Objects;

public class ChatPreferencesViewModel implements PreferencesViewModelBase {

    public static final List<Integer> CHAT_CACHE_SIZES = List.of(
            32, 64, 128, 256, 512, 1024, 2048
    );

    public static final List<Double> FONT_SIZES = List.of(
            8d, 9d, 10d, 10.5, 11d, 12d, 13d, 14d, 16d, 18d
    );

    private final ObjectProperty<Font> font;

    private final DoubleProperty fontSize;

    private final BooleanProperty showUserName;

    private final BooleanProperty showBadges;

    private final IntegerProperty chatCacheSize;

    private final ChatPreferences prefs;

    public ChatPreferencesViewModel() {
        prefs = AppPreferences.getInstance().getChatPreferences();
        showUserName = new SimpleBooleanProperty(prefs.isShowUserName());
        showBadges = new SimpleBooleanProperty(prefs.isShowBadges());
        chatCacheSize = new SimpleIntegerProperty(prefs.getChatCacheSize());

        var font = prefs.getFont();
        this.font = new SimpleObjectProperty<>(font.getFont());
        this.fontSize = new SimpleDoubleProperty(font.getSize());
    }

    @Override
    public void sync() {
        var font = getFont();
        var size = getFontSize();
        if (!Objects.equals(prefs.getFont().getFont(), font)
                || prefs.getFont().getSize() != size) {
            prefs.setFont(new ChatFont(font.getFamily(), size));
        }

        prefs.setShowUserName(isShowUserName());
        prefs.setShowBadges(isShowBadges());
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<Font> fontProperty() { return font; }
    public Font getFont() { return font.get(); }
    public void setFont(Font font) { this.font.set(font); }

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
