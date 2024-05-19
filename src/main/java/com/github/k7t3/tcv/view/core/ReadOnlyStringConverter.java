package com.github.k7t3.tcv.view.core;

import javafx.util.StringConverter;

public abstract class ReadOnlyStringConverter<T> extends StringConverter<T> {

    @Override
    public T fromString(String s) {
        throw new UnsupportedOperationException();
    }

}
