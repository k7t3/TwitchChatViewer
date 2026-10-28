/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.prefs;

import javafx.beans.property.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public abstract class PreferencesBase {
    protected final PreferenceStore store;
    private final Map<String, Consumer<Optional<String>>> updates = new HashMap<>();
    private boolean applying;
    private final Set<String> recoveryKeys;

    PreferencesBase(Preferences preferences) { this(preferences, Set.of()); }

    PreferencesBase(Preferences preferences, Set<String> recoveryKeys) {
        this(new PreferenceStore(preferences, null), recoveryKeys);
    }

    PreferencesBase(PreferenceStore store, Set<String> recoveryKeys) {
        this.store = java.util.Objects.requireNonNull(store);
        this.recoveryKeys = Set.copyOf(recoveryKeys);
    }

    final void applyCommittedValues(Map<String, Optional<String>> values) {
        var previous = applying;
        applying = true;
        try { values.forEach((key, value) -> { var update = updates.get(key); if (update != null) update.accept(value); }); }
        finally { applying = previous; }
    }

    protected BooleanProperty createBooleanProperty(PreferenceKey<Boolean> key) {
        var property = new SimpleBooleanProperty(key.read(store.read(key.name()))) {
            @Override public void set(boolean value) { checkWritable(); super.set(value); }
        };
        updates.put(key.name(), value -> property.set(key.read(value)));
        property.addListener((ob, old, next) -> write(key, next));
        return property;
    }

    protected IntegerProperty createIntegerProperty(PreferenceKey<Integer> key) {
        var property = new SimpleIntegerProperty(key.read(store.read(key.name()))) {
            @Override public void set(int value) { checkWritable(); key.encode(value); super.set(value); }
        };
        updates.put(key.name(), value -> property.set(key.read(value)));
        property.addListener((ob, old, next) -> write(key, next.intValue()));
        return property;
    }

    protected DoubleProperty createDoubleProperty(PreferenceKey<Double> key) {
        var property = new SimpleDoubleProperty(key.read(store.read(key.name()))) {
            @Override public void set(double value) { checkWritable(); key.encode(value); super.set(value); }
        };
        updates.put(key.name(), value -> property.set(key.read(value)));
        property.addListener((ob, old, next) -> write(key, next.doubleValue()));
        return property;
    }

    protected StringProperty createStringProperty(PreferenceKey<String> key) {
        var property = new SimpleStringProperty(key.read(store.read(key.name()))) {
            @Override public void set(String value) { checkWritable(); key.encode(value); super.set(value); }
        };
        updates.put(key.name(), value -> property.set(key.read(value)));
        property.addListener((ob, old, next) -> write(key, next));
        return property;
    }

    protected <T> ObjectProperty<T> createObjectProperty(PreferenceKey<T> key) {
        var property = new SimpleObjectProperty<T>(key.read(store.read(key.name()))) {
            @Override public void set(T value) { checkWritable(); key.encode(value); super.set(value); }
        };
        updates.put(key.name(), value -> property.set(key.read(value)));
        property.addListener((ob, old, next) -> write(key, next));
        return property;
    }

    private <T> void write(PreferenceKey<T> key, T value) {
        if (applying) return;
        var encoded = key.encode(value);
        store.write(key.name(), Optional.of(encoded), recoveryKeys.contains(key.name()));
    }

    private void checkWritable() { if (!applying) store.checkOpen(); }
}
