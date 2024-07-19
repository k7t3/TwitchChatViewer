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
    void testParse2() {
        var v = "9999.9999.9999";
        var version = Version.of(v);
        assertEquals(9999, version.major());
        assertEquals(9999, version.minor());
        assertEquals(9999, version.patch());
        assertEquals(v, version.toString());
    }

    @Test
    void testParse3() {
        var v = "Text is here";
        var version = Version.of(v);
        assertEquals(1, version.major());
        assertEquals(0, version.minor());
        assertEquals(0, version.patch());
        assertEquals("1.0.0", version.toString());
    }

}