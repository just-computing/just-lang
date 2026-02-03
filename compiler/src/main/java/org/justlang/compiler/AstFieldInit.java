package org.justlang.compiler;

public final class AstFieldInit {
    private final String name;
    private final AstExpr value;

    public AstFieldInit(String name, AstExpr value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public AstExpr value() {
        return value;
    }
}
