package org.justlang.runtime;

public final class Panic {
    public void panic(String message) {
        throw new RuntimeException(message);
    }
}
