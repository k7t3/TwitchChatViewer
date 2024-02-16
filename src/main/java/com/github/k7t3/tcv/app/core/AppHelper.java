package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.Twitch;
import javafx.beans.property.*;

import java.io.Closeable;

public class AppHelper implements Closeable {

    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper();

    private final ObjectProperty<Twitch> twitch = new SimpleObjectProperty<>();

    private AppHelper() {
        userId.bind(twitch.map(Twitch::getUserId));
        userName.bind(twitch.map(Twitch::getUserName));
        authorized.bind(twitch.isNotNull());
    }

    @Override
    public void close() {
        var twitch = getTwitch();

        if (twitch == null) return;

        twitch.close();
    }

    // ########################################
    // PROPERTIES

    public ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
    public String getUserId() { return userId.get(); }

    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }

    public ObjectProperty<Twitch> twitchProperty() { return twitch; }
    public Twitch getTwitch() { return twitch.get(); }
    public void setTwitch(Twitch twitch) { this.twitch.set(twitch); }

    public static AppHelper getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppHelper INSTANCE = new AppHelper();
    }
}
