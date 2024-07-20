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

package com.github.k7t3.tcv.domain.chat;

import java.util.AbstractList;
import java.util.List;

public class ChatMessage extends AbstractList<ChatMessageFragment> {

    private final List<ChatMessageFragment> fragments;

    private final String plain;

    public ChatMessage(String plain, List<ChatMessageFragment> fragments) {
        this.plain = plain;
        this.fragments = fragments;
    }

    public String getPlain() {
        return plain;
    }

    @Override
    public ChatMessageFragment get(int index) {
        return fragments.get(index);
    }

    @Override
    public int size() {
        return fragments.size();
    }

}
