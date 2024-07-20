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

package com.github.k7t3.tcv.app.emoji;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

public class EmojiProperties {

    private String name;
    private String url;
    private String version;
    private String hash;

    private boolean loaded = false;

    public void loadEmojiProperties() {
        if (loaded) return;
        loaded = true;
        try (var input = getClass().getResourceAsStream("emoji.properties")) {
            var properties = new Properties();
            properties.load(input);

            this.name = properties.getProperty("emoji.name");
            this.url = properties.getProperty("emoji.url");
            this.version = properties.getProperty("emoji.version");
            this.hash = properties.getProperty("emoji.hash");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        if (!loaded) throw new IllegalStateException("not loaded");
        return name;
    }

    public String getUrl() {
        if (!loaded) throw new IllegalStateException("not loaded");
        return url;
    }

    public String getVersion() {
        if (!loaded) throw new IllegalStateException("not loaded");
        return version;
    }

    public String getHash() {
        if (!loaded) throw new IllegalStateException("not loaded");
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmojiProperties that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(url, that.url) && Objects.equals(version, that.version) && Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, version, hash);
    }

    @Override
    public String toString() {
        return "EmojiProperties{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", version='" + version + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
