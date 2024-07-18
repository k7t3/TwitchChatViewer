package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.chat.ClipFinder;
import com.github.twitch4j.helix.domain.Clip;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class ClipFinderTest {

    @Test
    void findClip() {
        var api = Mockito.mock(TwitchAPI.class);
        var twitch = Mockito.mock(Twitch.class);
        Mockito.when(twitch.getTwitchAPI()).thenReturn(api);
        Mockito.when(api.getClips(List.of("StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq"))).thenReturn(List.of(new Clip()));
        Mockito.when(api.getClips(List.of("SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"))).thenReturn(List.of(new Clip()));

        var test = List.of(
                "https://clips.twitch.tv/StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq",
                "https://www.twitch.tv/fantasista_jp/clip/SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"
        );

        var finder = new ClipFinder(twitch);

        for (var link : test) {
            try {
                var op = finder.findClip(link);
                if (op.isEmpty()) {
                    fail("op is not present value");
                    return;
                }
            } catch (NullPointerException e) {
                // Clipが空のためNullPointerExceptionが発生するはず
            }
        }
    }
}