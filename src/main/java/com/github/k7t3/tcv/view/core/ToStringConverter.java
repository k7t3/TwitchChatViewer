package com.github.k7t3.tcv.view.core;

import javafx.util.StringConverter;

import java.util.function.Function;

public class ToStringConverter<T> extends StringConverter<T> {

    private final Function<T, String> toString;

    public ToStringConverter(Function<T, String> toString) {
        this.toString = toString;
    }

    @Override
    public String toString(T t) {
        return toString.apply(t);
    }

    @Override
    public T fromString(String s) {
        throw new UnsupportedOperationException();
    }

}
