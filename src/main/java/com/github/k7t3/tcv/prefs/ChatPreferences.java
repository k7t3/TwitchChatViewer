package com.github.k7t3.tcv.prefs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;

public class ChatPreferences extends PreferencesBase {

    /**
     * チャットビューで適用されるフォントファミリ
     */
    private static final String CHAT_FONT_FAMILY = "chat.font.family";

    /**
     * チャットビューでユーザー名を表示するか
     */
    private static final String CHAT_SHOW_USERNAME = "chat.show.username";

    /**
     * チャットビューでユーザーのバッジを表示するか
     */
    private static final String CHAT_SHOW_BADGES = "chat.show.badges";

    /** 透過ウインドウの透過度*/
    private static final String FLOATABLE_CHAT_OPACITY = "floatable.chat.opacity";

    /** 透過ウインドウを常に最前面に表示するか*/
    private static final String FLOATABLE_CHAT_TOP = "floatable.chat.always.top";

    private ObjectProperty<ChatFont> font;

    private BooleanProperty showUserName;

    private BooleanProperty showBadges;

    private DoubleProperty floatableChatOpacity;

    private BooleanProperty floatableChatAlwaysTop;

    ChatPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(CHAT_FONT_FAMILY, ChatFont.DEFAULT.getFamily());
        defaults.put(CHAT_SHOW_USERNAME, Boolean.TRUE);
        defaults.put(CHAT_SHOW_BADGES, Boolean.TRUE);
        defaults.put(FLOATABLE_CHAT_OPACITY, 0.7d);
        defaults.put(FLOATABLE_CHAT_TOP, Boolean.TRUE);
    }

    @Override
    protected void onImported() {
        if (!Objects.equals(getFont().getFamily(), get(CHAT_FONT_FAMILY))) {
            setFont(new ChatFont(CHAT_FONT_FAMILY));
        }

        if (isShowBadges() != getBoolean(CHAT_SHOW_BADGES)) {
            setShowBadges(getBoolean(CHAT_SHOW_BADGES));
        }

        if (isShowUserName() != getBoolean(CHAT_SHOW_USERNAME)) {
            setShowUserName(getBoolean(CHAT_SHOW_USERNAME));
        }

        if (isFloatableChatAlwaysTop() != getBoolean(FLOATABLE_CHAT_TOP)) {
            setFloatableChatAlwaysTop(getBoolean(FLOATABLE_CHAT_TOP));
        }

        if (getFloatableChatOpacity() != getDouble(FLOATABLE_CHAT_OPACITY)) {
            setFloatableChatOpacity(getDouble(FLOATABLE_CHAT_OPACITY));
        }
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<ChatFont> fontProperty() {
        if (font == null) {
            var family = get(CHAT_FONT_FAMILY);
            font = new SimpleObjectProperty<>(new ChatFont(family));
            font.addListener((ob, o, n) -> preferences.put(CHAT_FONT_FAMILY, n.getFamily()));
        }
        return font;
    }
    public ChatFont getFont() { return fontProperty().get(); }
    public void setFont(ChatFont font) { this.fontProperty().set(font); }

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

    public DoubleProperty floatableChatOpacityProperty() {
        if (floatableChatOpacity == null) floatableChatOpacity = createDoubleProperty(FLOATABLE_CHAT_OPACITY);
        return floatableChatOpacity;
    }
    public double getFloatableChatOpacity() { return floatableChatOpacityProperty().get(); }
    public void setFloatableChatOpacity(double floatableChatOpacity) { floatableChatOpacityProperty().set(floatableChatOpacity); }

    public BooleanProperty floatableChatAlwaysTopProperty() {
        if (floatableChatAlwaysTop == null) floatableChatAlwaysTop = createBooleanProperty(FLOATABLE_CHAT_TOP);
        return floatableChatAlwaysTop;
    }
    public boolean isFloatableChatAlwaysTop() { return floatableChatAlwaysTopProperty().get(); }
    public void setFloatableChatAlwaysTop(boolean floatableChatAlwaysTop) { floatableChatAlwaysTopProperty().set(floatableChatAlwaysTop); }
}
