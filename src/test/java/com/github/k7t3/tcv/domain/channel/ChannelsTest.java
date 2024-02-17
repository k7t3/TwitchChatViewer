package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.LogManager;

import static org.junit.jupiter.api.Assertions.*;

class ChannelsTest {

    private static Twitch twitch;

    @BeforeAll
    static void setUpAll() {
        try (var is = Channels.class.getResourceAsStream("/logging_test.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var loader = new TwitchLoader();
        loader.load().ifPresent(t -> twitch = t);
    }

    @AfterAll
    static void cleanUpAll() {
        if (twitch != null) {
            var client = twitch.getClient();
            var helper = client.getClientHelper();

            client.close();
            helper.close();
        }
    }

    @Test
    void getFollowedBroadcasters() {
        if (twitch == null) {
            fail("twitch is null");
            return;
        }
        var api = new Channels(twitch);
        var broadcasters = api.getFollowedBroadcasters();

        System.out.println(broadcasters);
    }
}