package org.justlang.interop;

import java.util.List;

public final class JvmListRef<T> extends JvmRef<List<T>> {
    public JvmListRef(List<T> value) {
        super(value);
    }

    public int size() {
        return get().size();
    }

    public T get(int index) {
        return get().get(index);
    }
}
