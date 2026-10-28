package com.github.k7t3.tcv.support;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

public final class MemoryPreferences extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();

    public MemoryPreferences() { super(null, ""); }
    MemoryPreferences(AbstractPreferences parent, String name) { super(parent, name); }
    @Override public boolean isUserNode() { return false; }
    @Override protected void putSpi(String key, String value) { values.put(key, value); }
    @Override protected String getSpi(String key) { return values.get(key); }
    @Override protected void removeSpi(String key) { values.remove(key); }
    @Override protected void removeNodeSpi() { values.clear(); }
    @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
    @Override protected String[] childrenNamesSpi() { return new String[0]; }
    @Override protected AbstractPreferences childSpi(String name) { return new MemoryPreferences(this, name); }
    @Override protected void syncSpi() {}
    @Override protected void flushSpi() {}
}
