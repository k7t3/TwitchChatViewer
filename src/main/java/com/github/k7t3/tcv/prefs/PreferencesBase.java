package com.github.k7t3.tcv.prefs;

import javafx.beans.property.*;

import java.util.Map;
import java.util.function.Function;
import java.util.prefs.Preferences;

public abstract class PreferencesBase {

    protected final Preferences preferences;

    protected final Map<String, Object> defaults;

    PreferencesBase(Preferences preferences, Map<String, Object> defaults) {
        this.preferences = preferences;
        this.defaults = defaults;
    }

    protected abstract void readFromPreferences();

    protected abstract void writeToPreferences();

    protected BooleanProperty createBooleanProperty(String key) {
        var property = new SimpleBooleanProperty(getBoolean(key));
        property.addListener((ob, o, n) -> preferences.putBoolean(key, n));
        return property;
    }

    protected IntegerProperty createIntegerProperty(String key) {
        var property = new SimpleIntegerProperty(getInt(key));
        property.addListener((ob, o, n) -> preferences.putInt(key, n.intValue()));
        return property;
    }

    protected DoubleProperty createDoubleProperty(String key) {
        var property = new SimpleDoubleProperty(getDouble(key));
        property.addListener((ob, o, n) -> preferences.putDouble(key, n.doubleValue()));
        return property;
    }

    protected StringProperty createStringProperty(String key) {
        var property = new SimpleStringProperty(get(key));
        property.addListener((ob, o, n) -> preferences.put(key, n));
        return property;
    }

    protected <T> ObjectProperty<T> createObjectProperty(
            String key,
            Function<String, T> fromString,
            Function<T, String> toString
    ) {
        var property = new SimpleObjectProperty<>(fromString.apply(get(key)));
        property.addListener((ob, o, n) -> preferences.put(key, toString.apply(n)));
        return property;
    }

    protected String get(String key) {
        return preferences.get(key, (String) defaults.get(key));
    }

    protected int getInt(String key) {
        return preferences.getInt(key, (Integer) defaults.get(key));
    }

    protected double getDouble(String key) {
        return preferences.getDouble(key, (Double) defaults.get(key));
    }

    protected boolean getBoolean(String key) {
        return preferences.getBoolean(key, (Boolean) defaults.get(key));
    }

    protected byte[] getByteArray(String key) {
        return preferences.getByteArray(key, (byte[]) defaults.get(key));
    }

}
