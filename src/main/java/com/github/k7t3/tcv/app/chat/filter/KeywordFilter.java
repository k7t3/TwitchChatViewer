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

import com.github.k7t3.tcv.domain.chat.ChatData;
import javafx.collections.ObservableList;

import java.util.function.Predicate;

class KeywordFilter implements Predicate<ChatData> {

    private final ObservableList<KeywordFilterEntry> entries;

    public KeywordFilter(ObservableList<KeywordFilterEntry> entries) {
        this.entries = entries;
    }

    @Override
    public boolean test(ChatData chatData) {
        return entries.stream().anyMatch(e -> e.test(chatData));
    }

}
