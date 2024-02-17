package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipFinderTest {

    private Twitch twitch;

    @BeforeEach
    void setUp() {
        var loader = new TwitchLoader();
        var op = loader.load();

        twitch = op.orElse(null);
    }

    @Test
    void findClip() {
        if (twitch == null) {
            fail("twitch is null");
            return;
        }

        var finder = new ClipFinder(twitch);
        var op = finder.findClip("https://www.twitch.tv/fantasista_jp/clip/SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V");
        if (op.isEmpty()) {
            fail("op is not present value");
            return;
        }

        System.out.println(op.get());
    }
}