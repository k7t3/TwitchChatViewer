package com.github.k7t3.tcv.app.theme;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JapaneseFontsTest {

    @Test
    void canSelectHiraginoSansFromTheAvailableFamilies() {
        var families = List.of("System", "Hiragino Sans GB", "Hiragino Sans");

        assertThat(JapaneseFonts.selectHiragino(families)).contains("Hiragino Sans");
    }

    @Test
    void leavesTheDefaultInPlaceWhenTheJapaneseHiraginoFamilyIsUnavailable() {
        var families = List.of("System", "Hiragino Sans GB", "BIZ UDGothic");

        assertThat(JapaneseFonts.selectHiragino(families)).isEmpty();
    }
}
