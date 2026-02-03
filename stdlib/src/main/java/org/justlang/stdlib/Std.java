package org.justlang.stdlib;

public final class Std {
    private Std() {
    }

    public static void print(org.justlang.stdlib.String value) {
        System.out.println(value.toJvm());
    }

    public static void print(java.lang.String value) {
        System.out.println(value);
    }

    public static void print(int value) {
        System.out.println(value);
    }

    public static void print(long value) {
        System.out.println(value);
    }

    public static void print(boolean value) {
        System.out.println(value);
    }

    public static void print(double value) {
        System.out.println(value);
    }
}
