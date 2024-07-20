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

package com.github.k7t3.tcv.app.prefs;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.text.Font;

public class FontFamily {

    private final ReadOnlyStringWrapper family;
    private final ReadOnlyObjectWrapper<Font> font;

    public FontFamily(String family) {
        this.family = new ReadOnlyStringWrapper(family);
        this.font = new ReadOnlyObjectWrapper<>(Font.font(family));
    }

    public ReadOnlyStringProperty familyProperty() { return family.getReadOnlyProperty(); }
    public String getFamily() { return family.get(); }

    public ReadOnlyObjectProperty<Font> fontProperty() { return font.getReadOnlyProperty(); }
    public Font getFont() { return font.get(); }

}
