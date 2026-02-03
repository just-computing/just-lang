package org.justlang.compiler;

public final class AstExprStmt implements AstStmt {
    private final AstExpr expr;

    public AstExprStmt(AstExpr expr) {
        this.expr = expr;
    }

    public AstExpr expr() {
        return expr;
    }
}
