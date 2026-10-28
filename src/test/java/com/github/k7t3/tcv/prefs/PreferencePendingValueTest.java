package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class PreferencePendingValueTest {
    @TempDir Path directory;

    @Test
    void aDatabaseVersionReadSeesItsAcceptedChangeBeforeTheBackendFinishes() throws Exception {
        var node = spy(new MemoryPreferences());
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("backend write was not released");
            return invocation.callRealMethod();
        }).when(node).put("database.version", "V_1");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        try {
            preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(preferences.getGeneralPreferences().getDatabaseVersion()).isEqualTo(DatabaseVersion.V_1);

        } finally { release.countDown(); preferences.save(); }
        assertThat(node.get("database.version", null)).isEqualTo("V_1");
    }
}
