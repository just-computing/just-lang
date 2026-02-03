package org.justlang.interop;

public class JvmRef<T> {
    private final T value;

    public JvmRef(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
