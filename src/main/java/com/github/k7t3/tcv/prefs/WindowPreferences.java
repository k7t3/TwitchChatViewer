package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.view.core.StageBounds;

import java.util.Map;
import java.util.prefs.Preferences;

public class WindowPreferences extends PreferencesBase {
    
    private final String xName;
    private final String yName;
    private final String wName;
    private final String hName;
    private final String maxName;
    
    WindowPreferences(Preferences preferences, Map<String, Object> defaults, String identify) {
        super(preferences, defaults);
        
        xName = "window.%s.x".formatted(identify);
        yName = "window.%s.y".formatted(identify);
        wName = "window.%s.width".formatted(identify);
        hName = "window.%s.height".formatted(identify);
        maxName = "window.%s.maximized".formatted(identify);

        defaults.put(xName, Double.NaN);
        defaults.put(yName, Double.NaN);
        defaults.put(wName, 1024d);
        defaults.put(hName, 768d);
        defaults.put(maxName, Boolean.FALSE);
    }

    @Override
    protected void onImported() {
        // no-op
    }

    public void setStageBounds(StageBounds bounds) {
        preferences.putDouble(xName, bounds.x());
        preferences.putDouble(yName, bounds.y());
        preferences.putDouble(wName, bounds.width());
        preferences.putDouble(hName, bounds.height());
        preferences.putBoolean(maxName, bounds.maximized());
    }

    public StageBounds getStageBounds() {
        var x = getDouble(xName);
        var y = getDouble(yName);
        var width = getDouble(wName);
        var height = getDouble(hName);
        var maximized = getBoolean(maxName);
        return new StageBounds(x, y, width, height, maximized);
    }
    
}
