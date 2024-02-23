package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.prefs.AppPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipFinderTest {

    private Twitch twitch;

    @BeforeEach
    void setUp() {
        var prefs = AppPreferences.getInstance();
        var loader = new TwitchLoader(prefs.getPreferences());
        var op = loader.load();

        twitch = op.orElse(null);
    }

    @Test
    void findClip() {
        if (twitch == null) {
            fail("twitch is null");
            return;
        }

        var test = List.of(
                "https://clips.twitch.tv/StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq",
                "https://www.twitch.tv/fantasista_jp/clip/SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"
        );

        var finder = new ClipFinder(twitch);

        for (var link : test) {
            var op = finder.findClip(link);
            if (op.isEmpty()) {
                fail("op is not present value");
                return;
            }

            System.out.println(op.get());
        }
    }
}