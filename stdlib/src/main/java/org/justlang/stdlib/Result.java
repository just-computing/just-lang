package org.justlang.stdlib;

import java.util.NoSuchElementException;

public final class Result<T, E> {
    private final T ok;
    private final E err;
    private final boolean isOk;

    private Result(T ok, E err, boolean isOk) {
        this.ok = ok;
        this.err = err;
        this.isOk = isOk;
    }

    public static <T, E> Result<T, E> ok(T value) {
        return new Result<>(value, null, true);
    }

    public static <T, E> Result<T, E> err(E error) {
        return new Result<>(null, error, false);
    }

    public boolean isOk() {
        return isOk;
    }

    public T unwrap() {
        if (!isOk) {
            throw new NoSuchElementException("Called unwrap on Err");
        }
        return ok;
    }

    public E unwrapErr() {
        if (isOk) {
            throw new NoSuchElementException("Called unwrapErr on Ok");
        }
        return err;
    }
}
