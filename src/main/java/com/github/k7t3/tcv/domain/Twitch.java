package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.clip.VideoClipRepository;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.TwitchChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TwitchのAPIアクセスに必要な情報を管理するクラス。
 */
public class Twitch implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Twitch.class);

    private final AtomicReference<OAuth2Credential> credential = new AtomicReference<>();

    private final AtomicReference<TwitchClient> client = new AtomicReference<>();

    private final TwitchClient chatClient;

    private final TwitchClientRefreshScheduler refreshScheduler;

    Twitch(OAuth2Credential credential, TwitchClient apiClient, TwitchClient chatClient) {
        this.chatClient = chatClient;
        setCredential(credential);
        setClient(apiClient);
        refreshScheduler = new TwitchClientRefreshScheduler(this);
        refreshScheduler.start();
    }

    private ChannelRepository channelRepository;

    public ChannelRepository getChannelRepository() {
        if (channelRepository == null) channelRepository = new ChannelRepository(this);
        return channelRepository;
    }

    private VideoClipRepository clipRepository;

    public VideoClipRepository getClipRepository() {
        if (clipRepository == null) clipRepository = new VideoClipRepository();
        return clipRepository;
    }

    void setClient(TwitchClient client) {
        this.client.set(client);
    }

    void setCredential(OAuth2Credential credential) {
        this.credential.set(credential);
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
        var client = getClient();
        if (client != null) {
            client.close();
        }
        chatClient.close();

        try {
            refreshScheduler.close();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
