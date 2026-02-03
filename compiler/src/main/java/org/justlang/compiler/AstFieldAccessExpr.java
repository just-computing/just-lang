package org.justlang.compiler;

public final class AstFieldAccessExpr implements AstExpr {
    private final AstExpr target;
    private final String field;

    public AstFieldAccessExpr(AstExpr target, String field) {
        this.target = target;
        this.field = field;
    }

    public AstExpr target() {
        return target;
    }

    public String field() {
        return field;
    }
}
