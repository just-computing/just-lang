package org.justlang.stdlib;

public final class String {
    private final java.lang.String value;

    public String(java.lang.String value) {
        this.value = value;
    }

    public static String fromJvm(java.lang.String value) {
        return new String(value);
    }

    public java.lang.String toJvm() {
        return value;
    }

    public int len() {
        return value.length();
    }

    @Override
    public java.lang.String toString() {
        return value;
    }
}
