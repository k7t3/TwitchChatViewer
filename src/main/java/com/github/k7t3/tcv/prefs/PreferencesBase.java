package com.github.k7t3.tcv.prefs;

import java.util.Map;
import java.util.prefs.Preferences;

public abstract class PreferencesBase {

    protected final Preferences preferences;

    protected final Map<String, Object> defaults;

    PreferencesBase(Preferences preferences, Map<String, Object> defaults) {
        this.preferences = preferences;
        this.defaults = defaults;
    }

}
