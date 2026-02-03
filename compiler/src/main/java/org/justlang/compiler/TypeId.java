package org.justlang.compiler;

public final class TypeId {
    private final String name;
    private final boolean struct;

    public static final TypeId STRING = new TypeId("String", false);
    public static final TypeId INT = new TypeId("Int", false);
    public static final TypeId BOOL = new TypeId("Bool", false);
    public static final TypeId VOID = new TypeId("Void", false);
    public static final TypeId UNKNOWN = new TypeId("Unknown", false);

    private TypeId(String name, boolean struct) {
        this.name = name;
        this.struct = struct;
    }

    public static TypeId struct(String name) {
        return new TypeId(name, true);
    }

    public static TypeId fromTypeName(String name) {
        return switch (name) {
            case "String", "std::String" -> STRING;
            case "i32", "int" -> INT;
            case "bool" -> BOOL;
            default -> UNKNOWN;
        };
    }

    public boolean isPrintable() {
        return this == STRING || this == INT || this == BOOL || struct;
    }

    public boolean isStruct() {
        return struct;
    }

    public String structName() {
        return struct ? name : null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TypeId that)) {
            return false;
        }
        return this.struct == that.struct && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + (struct ? 1 : 0);
    }
}
