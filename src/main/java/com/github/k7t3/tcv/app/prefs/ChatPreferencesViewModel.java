package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatFont;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import javafx.beans.property.*;

public class ChatPreferencesViewModel implements PreferencesViewModelBase {

    private final ObjectProperty<ChatFont> font;

    private final BooleanProperty showUserName;

    private final BooleanProperty showBadges;

    private final ChatPreferences prefs;

    public ChatPreferencesViewModel() {
        prefs = AppPreferences.getInstance().getChatPreferences();
        font = new SimpleObjectProperty<>(prefs.getFont());
        showUserName = new SimpleBooleanProperty(prefs.isShowUserName());
        showBadges = new SimpleBooleanProperty(prefs.isShowBadges());
    }

    @Override
    public void sync() {
        var font = getFont();
        if (!font.equals(prefs.getFont())) {
            prefs.setFont(font);
        }

        prefs.setShowUserName(isShowUserName());
        prefs.setShowBadges(isShowBadges());
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<ChatFont> fontProperty() { return font; }
    public ChatFont getFont() { return font.get(); }
    public void setFont(ChatFont font) { this.font.set(font); }

    public BooleanProperty showUserNameProperty() { return showUserName; }
    public boolean isShowUserName() { return showUserName.get(); }
    public void setShowUserName(boolean showUserName) { this.showUserName.set(showUserName); }

    public BooleanProperty showBadgesProperty() { return showBadges; }
    public boolean isShowBadges() { return showBadges.get(); }
    public void setShowBadges(boolean showBadges) { this.showBadges.set(showBadges); }

}
