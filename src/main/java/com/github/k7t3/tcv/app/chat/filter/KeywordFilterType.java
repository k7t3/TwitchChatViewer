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

package com.github.k7t3.tcv.app.chat.filter;

public enum KeywordFilterType {

    CONTAINS(0),
    PREFIX_MATCH(1),
    EXACT_MATCH(2),
    REGEXP(3);

    private final int type;

    KeywordFilterType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static KeywordFilterType of(int type) {
        for (var filterType : KeywordFilterType.values()) {
            if (filterType.type == type)
                return filterType;
        }
        throw new IllegalArgumentException("unknown type = " + type);
    }
}
