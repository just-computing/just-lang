package org.justlang.compiler;

import java.util.List;

public final class AstMatchExpr implements AstExpr {
    private final AstExpr target;
    private final List<AstMatchArm> arms;

    public AstMatchExpr(AstExpr target, List<AstMatchArm> arms) {
        this.target = target;
        this.arms = arms;
    }

    public AstExpr target() {
        return target;
    }

    public List<AstMatchArm> arms() {
        return arms;
    }
}
