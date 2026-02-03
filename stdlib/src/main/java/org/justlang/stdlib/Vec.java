package org.justlang.stdlib;

import java.util.ArrayList;
import java.util.List;

public final class Vec<T> {
    private final List<T> items = new ArrayList<>();

    public static <T> Vec<T> newVec() {
        return new Vec<>();
    }

    public void push(T item) {
        items.add(item);
    }

    public int len() {
        return items.size();
    }

    public T get(int index) {
        return items.get(index);
    }

    public List<T> asList() {
        return items;
    }
}
