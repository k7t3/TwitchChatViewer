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
import javafx.beans.property.*;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class KeywordFilterEntry implements Predicate<ChatData> {

    private final IntegerProperty filterId = new SimpleIntegerProperty();
    private final ObjectProperty<KeywordFilterType> filterType;
    private final StringProperty keyword;
    private final ObjectProperty<Pattern> pattern;

    private KeywordFilterEntry(KeywordFilterType filterType, String keyword, Pattern pattern) {
        this.filterType = new SimpleObjectProperty<>(filterType);
        this.keyword = new SimpleStringProperty(keyword);
        this.pattern = new SimpleObjectProperty<>(pattern);
    }

    public static KeywordFilterEntry prefixMatch(String keyword) {
        return new KeywordFilterEntry(KeywordFilterType.PREFIX_MATCH, keyword, null);
    }

    public static KeywordFilterEntry containsMatch(String keyword) {
        return new KeywordFilterEntry(KeywordFilterType.CONTAINS, keyword, null);
    }

    public static KeywordFilterEntry exactMatch(String keyword) {
        return new KeywordFilterEntry(KeywordFilterType.EXACT_MATCH, keyword, null);
    }

    public static KeywordFilterEntry regexMatch(Pattern pattern) {
        return new KeywordFilterEntry(KeywordFilterType.REGEXP, null, pattern);
    }

    private boolean startsWith(String message) {
        var keyword = getKeyword();
        return message.startsWith(keyword);
    }

    private boolean contains(String message) {
        var keyword = getKeyword();
        return message.contains(keyword);
    }

    private boolean equality(String message) {
        var keyword = getKeyword();
        return message.equals(keyword);
    }

    private boolean regex(String message) {
        var pattern = getPattern();
        return pattern.matcher(message).find();
    }

    @Override
    public boolean test(ChatData chatData) {
        var message = chatData.message().getPlain();
        var filterType = getFilterType();
        switch (filterType) {
            case CONTAINS -> {
                return contains(message);
            }
            case PREFIX_MATCH -> {
                return startsWith(message);
            }
            case EXACT_MATCH -> {
                return equality(message);
            }
            case REGEXP -> {
                return regex(message);
            }
            default -> throw new IllegalStateException("unknown filter type = " + filterType);
        }
    }

    public IntegerProperty filterIdProperty() { return filterId; }
    public int getFilterId() { return filterId.get(); }
    public void setFilterId(int filterId) { this.filterId.set(filterId); }

    public ObjectProperty<KeywordFilterType> filterTypeProperty() { return filterType; }
    public KeywordFilterType getFilterType() { return filterType.get(); }
    public void setFilterType(KeywordFilterType filterType) { this.filterType.set(filterType); }

    public StringProperty keywordProperty() { return keyword; }
    public String getKeyword() { return keyword.get(); }
    public void setKeyword(String keyword) { this.keyword.set(keyword); }

    public ObjectProperty<Pattern> patternProperty() { return pattern; }
    public Pattern getPattern() { return pattern.get(); }
    public void setPattern(Pattern pattern) { this.pattern.set(pattern); }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (KeywordFilterEntry) obj;
        return Objects.equals(this.filterType, that.filterType) &&
                Objects.equals(this.keyword, that.keyword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filterType, keyword);
    }

    @Override
    public String toString() {
        return "KeywordMessageFilterEntry[" +
                "filterType=" + filterType + ", " +
                "keyword=" + keyword + ']';
    }


}
