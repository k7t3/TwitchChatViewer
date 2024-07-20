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

package com.github.k7t3.tcv.app.chat;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import javafx.scene.paint.Color;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class DefinedChatColors {

    private final LoadingCache<String, Color> userColors = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .build(s -> getRandomColor());

    private final List<Color> colors = new ArrayList<>();

    DefinedChatColors() {
        colors.add(Color.web("#FF0000"));
        colors.add(Color.web("#0000FF"));
        colors.add(Color.web("#00FF00"));
        colors.add(Color.web("#B22222"));
        colors.add(Color.web("#FF7F50"));
        colors.add(Color.web("#9ACD32"));
        colors.add(Color.web("#FF4500"));
        colors.add(Color.web("#2E8B57"));
        colors.add(Color.web("#DAA520"));
        colors.add(Color.web("#D2691E"));
        colors.add(Color.web("#5F9EA0"));
        colors.add(Color.web("#1E90FF"));
        colors.add(Color.web("#FF69B4"));
        colors.add(Color.web("#8A2BE2"));
        colors.add(Color.web("#00FF7F"));
    }

    Color getRandom(String userId) {
        return userColors.get(userId);
    }

    private Color getRandomColor() {
        var i = (int) (Math.random() * Math.max(0, colors.size() - 1));
        return colors.get(i);
    }

}
