package org.justlang.compiler;

public final class AstBinaryExpr implements AstExpr {
    private final AstExpr left;
    private final String operator;
    private final AstExpr right;

    public AstBinaryExpr(AstExpr left, String operator, AstExpr right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public AstExpr left() {
        return left;
    }

    public String operator() {
        return operator;
    }

    public AstExpr right() {
        return right;
    }
}
