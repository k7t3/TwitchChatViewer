package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsCredentialCoordinationTest {
    @TempDir Path directory;

    @Test
    void editorAndCredentialsCanShareAFileLockWithoutOverlappingOrLosingEitherValue() throws Exception {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var lock = directory.resolve("preferences.lock");
        var encoding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var store = new PreferencesCredentialStorage(node, bytes -> {
            encoding.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("credential encoding was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            return bytes.clone();
        }, byte[]::clone, lock);
        var credential = new OAuth2Credential("synthetic", "synthetic-access", null, "owned", "Owned", 3600,
                List.of(), Map.of());
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var service = new SettingsSaveService(new AppPreferences(node, lock), new ChatFilterEntityStore(db));
            var savedCredential = worker.submit(() -> store.saveCredentials(List.of(credential)));
            try {
                assertThat(encoding.await(5, TimeUnit.SECONDS)).isTrue();
                var savedSettings = worker.submit(() -> service.save(new SettingsDraft(
                        Map.of("theme", Optional.of("NORD")), ChatFilterEntityStore.Changes.empty())));

                assertThatThrownBy(() -> savedSettings.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                savedCredential.get(5, TimeUnit.SECONDS);
                savedSettings.get(5, TimeUnit.SECONDS);

                assertThat(node.get("theme", null)).isEqualTo("NORD");
                assertThat(store.loadCredentials()).extracting(value -> ((OAuth2Credential) value).getAccessToken())
                        .containsExactly("synthetic-access");
            } finally { release.countDown(); }
        }
    }
}
