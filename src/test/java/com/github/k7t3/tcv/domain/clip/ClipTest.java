package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.prefs.AppPreferences;

import java.util.List;

public class ClipTest {

    public static void main(String...args) {
        var clipIds = List.of(
                "TolerantClumsyGazelleHassaanChop-aEM747vTXUf6HN_D"
        );

        var prefs = AppPreferences.getInstance();
        var loader = new TwitchLoader(prefs.getPreferences());
        var twitch = loader.load().orElse(null);

        if (twitch == null)
            return;

        try {

            var api = twitch.getTwitchAPI();

            var list = api.getClips(clipIds);

            list.forEach(System.out::println);

            System.out.println("done");

        } finally {
            twitch.close();
        }
    }

}
