package com.github.k7t3.tcv.app.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    @Test
    void testParse() {
        var v = "1.2.0";
        var version = Version.of(v);
        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
        assertEquals(v, version.toString());
    }

    @Test
    void testDevParse() {
        var v = "1.2.0-dev";
        var version = Version.of(v);
        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
        assertTrue(version.isDevelopmentVersion());
        assertEquals("dev", version.developmentCode());
        assertEquals(v, version.toString());
    }

    @Test
    void testParse2() {
        var v = "9999.9999.9999";
        var version = Version.of(v);
        assertEquals(9999, version.major());
        assertEquals(9999, version.minor());
        assertEquals(9999, version.patch());
        assertEquals(v, version.toString());
    }

    @Test
    void testDevParse2() {
        var v = "9999.9999.9999-development";
        var version = Version.of(v);
        assertEquals(9999, version.major());
        assertEquals(9999, version.minor());
        assertEquals(9999, version.patch());
        assertTrue(version.isDevelopmentVersion());
        assertEquals("development", version.developmentCode());
        assertEquals(v, version.toString());
    }

    @Test
    void failTest() {
        var v = "Text is here";
        var version = Version.of(v);

        // パースに失敗するためデフォルトの開発バージョンが返されるはず
        assertEquals(1, version.major());
        assertEquals(0, version.minor());
        assertEquals(0, version.patch());
        assertTrue(version.toString().endsWith("dev"));
    }

    @Test
    void failTest2() {
        var v = "1.1.1_hello";
        var version = Version.of(v);

        // パースに失敗するためデフォルトの開発バージョンが返されるはず
        assertEquals(1, version.major());
        assertEquals(0, version.minor());
        assertEquals(0, version.patch());
        assertTrue(version.toString().endsWith("dev"));
    }

}