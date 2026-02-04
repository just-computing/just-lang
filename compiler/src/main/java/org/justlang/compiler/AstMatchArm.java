package org.justlang.compiler;

public final class AstMatchArm {
    private final AstMatchPattern pattern;
    private final AstExpr guard;
    private final AstExpr expr;

    public AstMatchArm(AstMatchPattern pattern, AstExpr guard, AstExpr expr) {
        this.pattern = pattern;
        this.guard = guard;
        this.expr = expr;
    }

    public AstMatchPattern pattern() {
        return pattern;
    }

    public AstExpr guard() {
        return guard;
    }

    public AstExpr expr() {
        return expr;
    }
}
