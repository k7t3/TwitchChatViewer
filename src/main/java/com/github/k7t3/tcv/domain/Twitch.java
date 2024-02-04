package com.github.k7t3.tcv.domain;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;

public class Twitch {

    private OAuth2Credential credential;

    private TwitchClient client;

    public void update(OAuth2Credential credential, TwitchClient client) {
        this.credential = credential;
        this.client = client;
    }

    public OAuth2Credential getCredential() {
        return credential;
    }

    public String getAccessToken() {
        return credential.getAccessToken();
    }

    public String getUserId() {
        return credential.getUserId();
    }

    public String getUserName() {
        return credential.getUserName();
    }

    public TwitchClient getClient() {
        return client;
    }
}
