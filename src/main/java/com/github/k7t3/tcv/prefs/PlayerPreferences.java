package com.github.k7t3.tcv.prefs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;

import java.util.Map;
import java.util.prefs.Preferences;

public class PlayerPreferences extends PreferencesBase {

    private static final String MUTED = "player.mute";
    private static final String VOLUME = "player.volume";
    private static final String AUTO = "player.auto";

    private BooleanProperty muted;

    private DoubleProperty volume;

    private BooleanProperty auto;

    PlayerPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);
        init(defaults);
    }

    private void init(Map<String, Object> defaults) {
        defaults.put(MUTED, Boolean.FALSE);
        defaults.put(VOLUME, 0.5);
        defaults.put(AUTO, Boolean.TRUE);
    }

    @Override
    protected void readFromPreferences() {
        if (isMuted() != getBoolean(MUTED)) {
            setMuted(getBoolean(MUTED));
        }

        if (getVolume() != getDouble(VOLUME)) {
            setVolume(getDouble(VOLUME));
        }

        if (isAuto() != getBoolean(AUTO)) {
            setAuto(getBoolean(AUTO));
        }
    }

    @Override
    protected void writeToPreferences() {
        if (muted != null) {
            preferences.putBoolean(MUTED, muted.get());
        }

        if (volume != null) {
            preferences.putDouble(VOLUME, volume.get());
        }

        if (auto != null) {
            preferences.putBoolean(AUTO, auto.get());
        }
    }

    // ******************** PROPERTIES ********************

    public BooleanProperty mutedProperty() {
        if (muted == null) muted = createBooleanProperty(MUTED);
        return muted;
    }
    public boolean isMuted() { return mutedProperty().get(); }
    public void setMuted(boolean muted) { mutedProperty().set(muted); }

    public DoubleProperty volumeProperty() {
        if (volume == null) volume = createDoubleProperty(VOLUME);
        return volume;
    }
    public double getVolume() { return volumeProperty().get(); }
    public void setVolume(double volume) { volumeProperty().set(volume); }

    public BooleanProperty autoProperty() {
        if (auto == null) auto = createBooleanProperty(AUTO);
        return auto;
    }
    public boolean isAuto() { return autoProperty().get(); }
    public void setAuto(boolean auto) { autoProperty().set(auto); }
}
