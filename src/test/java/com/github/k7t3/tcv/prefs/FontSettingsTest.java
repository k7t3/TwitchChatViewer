package com.github.k7t3.tcv.prefs;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FontSettingsTest {
    @Test void theExistingBinaryFormatRoundTripsWithoutJavaFx() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { out.writeUTF("Missing family 日本語"); out.writeDouble(19.5); }
        var settings = FontSettings.deserialize(bytes.toByteArray());
        assertThat(settings.family()).isEqualTo("Missing family 日本語");
        assertThat(settings.size()).isEqualTo(19.5);
        assertThat(settings.serialize()).containsExactly(bytes.toByteArray());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidSizesAreRejectedAtTheDataBoundary(double size) {
        assertThatThrownBy(() -> new FontSettings("System", size)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankFamiliesAreRejectedAtTheDataBoundary(String family) {
        assertThatThrownBy(() -> new FontSettings(family, 16)).isInstanceOf(IllegalArgumentException.class);
    }
}
