package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.TwitchLoader;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClipTest {

    public static void main(String...args) {
        var clipIds = List.of(
                "RepleteBillowingCakeCurseLit-lZ_Rxlj4hB6GI2uv",
                "StylishDullDaikonAMPEnergy-pTmLJ5dazGRB32rq",
                "SecretiveSpunkyFlyCmonBruh-7Zz3Psy23ZeN2O7V"
        );

        var loader = new TwitchLoader();
        var twitch = loader.load().orElse(null);

        if (twitch == null)
            return;

        try {

            for (var clipId : clipIds) {

                TimeUnit.SECONDS.sleep(2);

                var helix = twitch.getClient().getHelix();

                var command = helix.getClips(
                        twitch.getAccessToken(),
                        null,
                        null,
                        List.of(clipId),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                var list = command.execute();
                System.out.println(list.getData());

            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            twitch.close();
        }
    }

}
