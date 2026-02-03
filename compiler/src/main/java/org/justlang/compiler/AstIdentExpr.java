package org.justlang.compiler;

public final class AstIdentExpr implements AstExpr {
    private final String name;

    public AstIdentExpr(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
