package org.justlang.stdlib;

import java.util.HashMap;

public final class Map<K, V> {
    private final java.util.Map<K, V> items = new HashMap<>();

    public Option<V> get(K key) {
        V value = items.get(key);
        if (value == null) {
            return Option.none();
        }
        return Option.some(value);
    }

    public void insert(K key, V value) {
        items.put(key, value);
    }
}
