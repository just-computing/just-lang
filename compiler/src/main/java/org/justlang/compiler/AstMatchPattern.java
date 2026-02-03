package org.justlang.compiler;

public final class AstMatchPattern {
    public enum Kind {
        WILDCARD,
        INT,
        BOOL,
        STRING
    }

    private final Kind kind;
    private final String value;

    public AstMatchPattern(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public Kind kind() {
        return kind;
    }

    public String value() {
        return value;
    }
}
