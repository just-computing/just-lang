package org.justlang.stdlib;

import java.util.NoSuchElementException;

public final class Option<T> {
    private final T value;
    private final boolean isSome;

    private Option(T value, boolean isSome) {
        this.value = value;
        this.isSome = isSome;
    }

    public static <T> Option<T> some(T value) {
        return new Option<>(value, true);
    }

    public static <T> Option<T> none() {
        return new Option<>(null, false);
    }

    public boolean isSome() {
        return isSome;
    }

    public T unwrap() {
        if (!isSome) {
            throw new NoSuchElementException("Called unwrap on None");
        }
        return value;
    }
}
