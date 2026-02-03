package org.justlang.stdlib;

public final class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    public static <T> Box<T> of(T value) {
        return new Box<>(value);
    }

    public T get() {
        return value;
    }
}
