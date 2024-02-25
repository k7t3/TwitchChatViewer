package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.clip.VideoClipRepository;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.TwitchChat;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class Twitch implements Closeable {

    private final AtomicReference<OAuth2Credential> credential = new AtomicReference<>();

    private final AtomicReference<TwitchClient> client = new AtomicReference<>();

    private final TwitchClient chatClient;

    private final CredentialStore credentialStore;

    private ChannelRepository channelRepository;

    private VideoClipRepository clipRepository;

    private TwitchAPI twitchAPI;

    Twitch(
            OAuth2Credential credential,
            CredentialStore credentialStore,
            TwitchClient apiClient,
            TwitchClient chatClient
    ) {
        this.chatClient = chatClient;
        this.credentialStore = credentialStore;
        setCredential(credential);
        setClient(apiClient);
    }

    public ChannelRepository getChannelRepository() {
        if (channelRepository == null) channelRepository = new ChannelRepository(this);
        return channelRepository;
    }

    public VideoClipRepository getClipRepository() {
        if (clipRepository == null) clipRepository = new VideoClipRepository();
        return clipRepository;
    }

    public TwitchAPI getTwitchAPI() {
        if (twitchAPI == null) twitchAPI = new TwitchAPI(this);
        return twitchAPI;
    }

    void setClient(TwitchClient client) {
        this.client.set(client);
    }

    void setCredential(OAuth2Credential credential) {
        this.credential.set(credential);
    }

    CredentialStore getCredentialStore() {
        return credentialStore;
    }

    private OAuth2Credential getCredential() {
        return credential.get();
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
        return client.get();
    }

    public TwitchChat getChat() {
        return chatClient.getChat();
    }

    public void logout() {
        if (clipRepository != null) {
            clipRepository.clear();
            clipRepository = null;
        }

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
        setClient(null);
        setCredential(null);
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

        var client = getClient();
        if (client != null) {
            client.close();
        }
        chatClient.close();
    }

}
