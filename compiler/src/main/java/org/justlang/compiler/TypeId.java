package org.justlang.compiler;

public final class TypeId {
    private final Kind kind;
    private final String name;
    private final TypeId first;
    private final TypeId second;

    public static final TypeId STRING = new TypeId(Kind.STRING, null, null, null);
    public static final TypeId INT = new TypeId(Kind.INT, null, null, null);
    public static final TypeId BOOL = new TypeId(Kind.BOOL, null, null, null);
    public static final TypeId ANY = new TypeId(Kind.ANY, null, null, null);
    public static final TypeId INFER = new TypeId(Kind.INFER, null, null, null);
    public static final TypeId VOID = new TypeId(Kind.VOID, null, null, null);
    public static final TypeId UNKNOWN = new TypeId(Kind.UNKNOWN, null, null, null);

    private TypeId(Kind kind, String name, TypeId first, TypeId second) {
        this.kind = kind;
        this.name = name;
        this.first = first;
        this.second = second;
    }

    public static TypeId struct(String name) {
        return new TypeId(Kind.STRUCT, name, null, null);
    }

    public static TypeId enumType(String name) {
        return new TypeId(Kind.ENUM, name, null, null);
    }

    public static TypeId option(TypeId inner) {
        return new TypeId(Kind.OPTION, "Option", inner, null);
    }

    public static TypeId result(TypeId ok, TypeId err) {
        return new TypeId(Kind.RESULT, "Result", ok, err);
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
        return this == STRING || this == INT || this == BOOL || this == ANY || isEnumLike();
    }

    public boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    public boolean isEnum() {
        return kind == Kind.ENUM || kind == Kind.OPTION || kind == Kind.RESULT;
    }

    public boolean isEnumLike() {
        return isEnum();
    }

    public String structName() {
        return kind == Kind.STRUCT ? name : null;
    }

    public String enumName() {
        return isEnum() ? name : null;
    }

    public boolean isOption() {
        return kind == Kind.OPTION;
    }

    public boolean isResult() {
        return kind == Kind.RESULT;
    }

    public TypeId optionInner() {
        return kind == Kind.OPTION ? first : null;
    }

    public TypeId resultOk() {
        return kind == Kind.RESULT ? first : null;
    }

    public TypeId resultErr() {
        return kind == Kind.RESULT ? second : null;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case STRING -> "String";
            case INT -> "Int";
            case BOOL -> "Bool";
            case ANY -> "Any";
            case INFER -> "_";
            case VOID -> "Void";
            case STRUCT, ENUM -> name;
            case OPTION -> "Option<" + first + ">";
            case RESULT -> "Result<" + first + ", " + second + ">";
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
        return this.kind == that.kind
            && java.util.Objects.equals(this.name, that.name)
            && java.util.Objects.equals(this.first, that.first)
            && java.util.Objects.equals(this.second, that.second);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, name, first, second);
    }

    private enum Kind {
        STRING,
        INT,
        BOOL,
        ANY,
        INFER,
        VOID,
        STRUCT,
        ENUM,
        OPTION,
        RESULT,
        UNKNOWN
    }
}
