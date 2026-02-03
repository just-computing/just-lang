package org.justlang.stdlib;

import java.util.List;

public final class Slice<T> {
    private final List<T> items;

    public Slice(List<T> items) {
        this.items = items;
    }

    public int len() {
        return items.size();
    }

    public T get(int index) {
        return items.get(index);
    }
}
