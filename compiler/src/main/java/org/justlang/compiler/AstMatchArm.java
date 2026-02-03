package org.justlang.compiler;

public final class AstMatchArm {
    private final AstMatchPattern pattern;
    private final AstExpr expr;

    public AstMatchArm(AstMatchPattern pattern, AstExpr expr) {
        this.pattern = pattern;
        this.expr = expr;
    }

    public AstMatchPattern pattern() {
        return pattern;
    }

    public AstExpr expr() {
        return expr;
    }
}
