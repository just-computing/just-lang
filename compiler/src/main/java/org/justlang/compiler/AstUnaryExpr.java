package org.justlang.compiler;

public final class AstUnaryExpr implements AstExpr {
    private final String operator;
    private final AstExpr expr;

    public AstUnaryExpr(String operator, AstExpr expr) {
        this.operator = operator;
        this.expr = expr;
    }

    public String operator() {
        return operator;
    }

    public AstExpr expr() {
        return expr;
    }
}
