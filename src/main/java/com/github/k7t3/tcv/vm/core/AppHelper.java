package com.github.k7t3.tcv.vm.core;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.stage.Stage;

import java.io.Closeable;

public class AppHelper implements Closeable {

    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private Twitch twitch = null;

    private AppHelper() {
    }

    @Override
    public void close() {
        if (twitch == null) return;
        var client = twitch.getClient();
        client.close();
    }

    public void update(OAuth2Credential credential, TwitchClient client) {
        twitch = new Twitch();
        twitch.update(credential, client);
        userId.set(credential.getUserId());
        userName.set(credential.getUserName());
    }

    public Twitch getTwitch() {
        return twitch;
    }

    // ########################################
    // PROPERTIES

    public ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
    public String getUserId() { return userId.get(); }

    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public static AppHelper getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppHelper INSTANCE = new AppHelper();
    }
}
