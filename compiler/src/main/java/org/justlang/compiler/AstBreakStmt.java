package org.justlang.compiler;

public final class AstBreakStmt implements AstStmt {
    private final String label;
    private final AstExpr expr;

    public AstBreakStmt(String label, AstExpr expr) {
        this.label = label;
        this.expr = expr;
    }

    public String label() {
        return label;
    }

    public AstExpr expr() {
        return expr;
    }
}
