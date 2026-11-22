package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.prefs.SettingsDraft;
import com.github.k7t3.tcv.app.prefs.SettingsSaveService;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.support.MemoryPreferences;
import javafx.application.Platform;

/** JavaFX を起動しない別 JVM で、背景の設定保存を実行する。実ユーザーの設定は使わない。 */
public final class PreferenceProcessProbe {
    public static void main(String[] arguments) {
        if (Platform.isFxApplicationThread()) throw new AssertionError("unexpected FX thread");
        var node = new MemoryPreferences();
        var prefs = new AppPreferences(node);
        var font = new FontSettings("Unavailable family 日本語", 19.5);
        var draft = new SettingsDraft(ChatPreferences.editorValues(font, false, false, 192), ChatFilterEntityStore.Changes.empty());
        try (var db = new TestDBConnector()) {
            db.connect();
            new SettingsSaveService(prefs, new ChatFilterEntityStore(db)).save(draft);
            if (!font.equals(prefs.getChatPreferences().getFont())) throw new AssertionError("font settings changed");
        }
        System.out.println("settings saved without starting JavaFX");
    }
}
