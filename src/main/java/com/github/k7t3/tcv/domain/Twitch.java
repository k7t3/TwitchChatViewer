package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.TwitchChat;

import java.io.Closeable;
import java.io.IOException;

public class Twitch implements Closeable {

    private final OAuth2Credential credential;

    private final TwitchClient client;

    private final CredentialStore credentialStore;

    private ChannelRepository channelRepository;

    private TwitchAPI twitchAPI;

    Twitch(
            OAuth2Credential credential,
            CredentialStore credentialStore,
            TwitchClient client
    ) {
        this.credentialStore = credentialStore;
        this.credential = credential;
        this.client = client;
    }

    public ChannelRepository getChannelRepository() {
        if (channelRepository == null) channelRepository = new ChannelRepository(this);
        return channelRepository;
    }

    public TwitchAPI getTwitchAPI() {
        if (twitchAPI == null) twitchAPI = new TwitchAPI(this);
        return twitchAPI;
    }

    void updateCredential(OAuth2Credential newOne) {
        getCredential().updateCredential(newOne);
    }

    CredentialStore getCredentialStore() {
        return credentialStore;
    }

    private OAuth2Credential getCredential() {
        return credential;
    }

    public String getAccessToken() {
        return getCredential().getAccessToken();
    }

    public String getUserId() {
        return getCredential().getUserId();
    }

    public String getUserName() {
        return getCredential().getUserName();
    }

    /**
     * ドメインパッケージでの使用に限りたい。
     */
    public TwitchClient getClient() {
        return client;
    }

    public TwitchChat getChat() {
        return client.getChat();
    }

    public void logout() {
        if (channelRepository != null) {
            channelRepository.clear();
            try {
                channelRepository.close();
            } catch (IOException ignored) {
            }
            channelRepository = null;
        }

        if (twitchAPI != null) {
            try {
                twitchAPI.close();
            } catch (IOException ignored) {
            }
            twitchAPI = null;
        }

        var credentialController = new CredentialController(credentialStore);
        try {
            credentialController.revokeToken();
        } finally {
            credentialController.disposeAuthenticate();
        }

        credentialStore.clearCredentials();
    }

    @Override
    public void close() {
        if (channelRepository != null) {
            try {
                channelRepository.close();
            } catch (IOException ignored) {
            }
        }

        if (twitchAPI != null) {
            try {
                twitchAPI.close();
            } catch (IOException ignored) {
            }
        }

        client.close();
    }

}
