package com.github.k7t3.tcv.prefs;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PreferenceValuesCodecCompatibilityTest {
    @Test
    void existingRecoveryRecordsKeepTheirEncodingAndDeletedValues() {
        // 共通化前のSettingsJournalCodecが出力した固定記録。現在のcodecから生成しない。
        var legacy = Base64.getDecoder().decode(
                "AAAAAQAAAAQAEGNoYXQuc2hvdy5iYWRnZXMBAAVmYWxzZQAHZXhhbXBsZQEACeaXpeacrOiqngAFdGhlbWUBAAROT1JEAAp0aGVtZS50eXBlAA==");
        var values = Map.of("theme", Optional.of("NORD"), "theme.type", Optional.<String>empty(),
                "chat.show.badges", Optional.of("false"), "example", Optional.of("日本語"));

        assertThat(PreferenceValuesCodec.decode(legacy)).isEqualTo(values);
        assertThat(PreferenceValuesCodec.encode(values)).containsExactly(legacy);
    }
}
