package com.github.k7t3.tcv.vm.core;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ModifiableObservableListBase;

import java.util.LinkedList;

public class LimitedObservableList<T> extends ModifiableObservableListBase<T> {

    private static final int DEFAULT_LIMIT_SIZE = 256;

    private final IntegerProperty limit;

    private final LinkedList<T> delegate = new LinkedList<>();

    public LimitedObservableList() {
        this(DEFAULT_LIMIT_SIZE);
    }

    public LimitedObservableList(int limit) {
        if (limit < 1) throw new IllegalArgumentException();
        this.limit = new SimpleIntegerProperty(limit);
        this.limit.addListener(this::changed);
    }

    private void changed(ObservableValue<? extends Number> ob, Number o, Number n) {
        var diff = n.intValue() - o.intValue();

        if (0 < diff) {
            for (var i = 0; i < diff; i++) {
                removeFirst();
            }
        }
    }

    @Override
    public T get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    protected void doAdd(int index, T element) {
        var i = index;
        if (limit.get() < size() + 1) {
            removeFirst();
            i--;
        }
        delegate.add(i, element);
    }

    @Override
    protected T doSet(int index, T element) {
        return delegate.set(index, element);
    }

    @Override
    protected T doRemove(int index) {
        return delegate.remove(index);
    }

    public IntegerProperty limitProperty() { return limit; }
    public int getLimit() { return limit.get(); }
    public void setLimit(int limit) { this.limit.set(limit); }

}
