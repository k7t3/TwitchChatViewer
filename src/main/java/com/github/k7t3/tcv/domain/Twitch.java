package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.clip.VideoClipRepository;
import com.github.philippheuer.credentialmanager.api.IStorageBackend;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.TwitchChat;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * TwitchのAPIアクセスに必要な情報を管理するクラス。
 */
public class Twitch implements Closeable {

    private final AtomicReference<OAuth2Credential> credential = new AtomicReference<>();

    private final AtomicReference<TwitchClient> client = new AtomicReference<>();

    private final TwitchClient chatClient;

    private final IStorageBackend credentialStorageBackend;

    private ChannelRepository channelRepository;

    private VideoClipRepository clipRepository;

    private TwitchAPI twitchAPI;

    Twitch(
            OAuth2Credential credential,
            IStorageBackend credentialStorageBackend,
            TwitchClient apiClient,
            TwitchClient chatClient
    ) {
        this.chatClient = chatClient;
        this.credentialStorageBackend = credentialStorageBackend;
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

    IStorageBackend getCredentialStorageBackend() {
        return credentialStorageBackend;
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

    @Override
    public void close() {
        if (channelRepository != null) {
            channelRepository.close();
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
