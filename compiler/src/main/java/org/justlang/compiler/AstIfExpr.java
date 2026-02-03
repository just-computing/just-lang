package org.justlang.compiler;

public final class AstIfExpr implements AstExpr {
    private final AstExpr condition;
    private final AstExpr thenExpr;
    private final AstExpr elseExpr;

    public AstIfExpr(AstExpr condition, AstExpr thenExpr, AstExpr elseExpr) {
        this.condition = condition;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
    }

    public AstExpr condition() {
        return condition;
    }

    public AstExpr thenExpr() {
        return thenExpr;
    }

    public AstExpr elseExpr() {
        return elseExpr;
    }
}
