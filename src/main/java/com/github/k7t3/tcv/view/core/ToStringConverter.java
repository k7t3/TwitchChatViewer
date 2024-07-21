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
