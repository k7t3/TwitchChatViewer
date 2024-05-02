package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.twitch4j.helix.domain.Clip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipFinderTest {

    private ClipFinder finder;

    @BeforeEach
    void setUp() {
        var api = Mockito.mock(TwitchAPI.class);
        var twitch = Mockito.mock(Twitch.class);
        Mockito.when(twitch.getTwitchAPI()).thenReturn(api);
        finder = new ClipFinder(twitch);
    }

    @Test
    void findClip() {
        var link = "https://clips.twitch.tv/MoldyDignifiedTrayBrokeBack-APxtCwp0MufhED4b";
        var url = finder.parseClipURI(link);
        assertNotNull(url);

        link = "https://clips.twitch.tv/";
        url = finder.parseClipURI(link);
        assertNull(url);

        link = "https://www.google.co.jp";
        url = finder.parseClipURI(link);
        assertNull(url);
    }
}