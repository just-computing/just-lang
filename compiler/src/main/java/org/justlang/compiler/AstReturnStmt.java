package org.justlang.compiler;

public final class AstReturnStmt implements AstStmt {
    private final AstExpr expr;

    public AstReturnStmt(AstExpr expr) {
        this.expr = expr;
    }

    public AstExpr expr() {
        return expr;
    }
}
