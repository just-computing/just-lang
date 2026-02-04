package org.justlang.compiler;

public final class TypeId {
    private final Kind kind;
    private final String name;

    public static final TypeId STRING = new TypeId(Kind.STRING, null);
    public static final TypeId INT = new TypeId(Kind.INT, null);
    public static final TypeId BOOL = new TypeId(Kind.BOOL, null);
    public static final TypeId ANY = new TypeId(Kind.ANY, null);
    public static final TypeId VOID = new TypeId(Kind.VOID, null);
    public static final TypeId UNKNOWN = new TypeId(Kind.UNKNOWN, null);

    private TypeId(Kind kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    public static TypeId struct(String name) {
        return new TypeId(Kind.STRUCT, name);
    }

    public static TypeId enumType(String name) {
        return new TypeId(Kind.ENUM, name);
    }

    public static TypeId fromTypeName(String name) {
        return switch (name) {
            case "String", "std::String" -> STRING;
            case "i32", "int" -> INT;
            case "bool" -> BOOL;
            case "Any", "std::Any" -> ANY;
            case "void" -> VOID;
            default -> UNKNOWN;
        };
    }

    public boolean isPrintable() {
        return this == STRING || this == INT || this == BOOL || this == ANY || kind == Kind.STRUCT || kind == Kind.ENUM;
    }

    public boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    public boolean isEnum() {
        return kind == Kind.ENUM;
    }

    public String structName() {
        return kind == Kind.STRUCT ? name : null;
    }

    public String enumName() {
        return kind == Kind.ENUM ? name : null;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case STRING -> "String";
            case INT -> "Int";
            case BOOL -> "Bool";
            case ANY -> "Any";
            case VOID -> "Void";
            case STRUCT, ENUM -> name;
            case UNKNOWN -> "Unknown";
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TypeId that)) {
            return false;
        }
        return this.kind == that.kind && java.util.Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, name);
    }

    private enum Kind {
        STRING,
        INT,
        BOOL,
        ANY,
        VOID,
        STRUCT,
        ENUM,
        UNKNOWN
    }
}
