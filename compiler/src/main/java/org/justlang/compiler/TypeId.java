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
        return this == STRING || this == INT || this == BOOL;
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
}
