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
