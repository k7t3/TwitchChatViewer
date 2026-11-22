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

package com.github.k7t3.tcv.view.chat;

import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFontTest {

    @Test
    void defaultFontUsesHiraginoWhenInstalled() {
        var expectedFamily = Font.getFamilies().contains("Hiragino Sans") ? "Hiragino Sans" : Font.getDefault().getFamily();

        var chatFont = ChatFont.getDefault();

        assertThat(chatFont.getFont()).isEqualTo(Font.font(expectedFamily, Font.getDefault().getSize()));
        assertThat(chatFont).isEqualTo(new ChatFont(expectedFamily, Font.getDefault().getSize()));
    }
}
