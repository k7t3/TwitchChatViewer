package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.twitch4j.helix.domain.Clip;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipFinderTest {

    private static Twitch twitch;

    @BeforeAll
    static void setupTwitch() {
        var api = Mockito.mock(TwitchAPI.class);
        var twitch = Mockito.mock(Twitch.class);
        Mockito.when(twitch.getTwitchAPI()).thenReturn(api);
        Mockito.when(api.getClips(List.of("StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq"))).thenReturn(List.of(new Clip()));
        Mockito.when(api.getClips(List.of("SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"))).thenReturn(List.of(new Clip()));
        ClipFinderTest.twitch = twitch;
    }

    private ClipFinder finder;

    @BeforeEach
    void setUp() {
        finder = new ClipFinder(twitch);
    }

    @Test
    void findTest() {
        var test = List.of(
                "https://clips.twitch.tv/StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq",
                "https://www.twitch.tv/fantasista_jp/clip/SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"
        );

        var finder = new ClipFinder(twitch);

        for (var link : test) {
            try {
                finder.findClip(link);
                fail();
            } catch (NullPointerException e) {
                // Clipが空のためNullPointerExceptionが発生するはず
            }
        }
    }

    @Test
    void parseTest() {
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