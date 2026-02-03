package org.justlang.compiler;

import java.util.List;

public final class AstCallExpr implements AstExpr {
    private final List<String> callee;
    private final List<AstExpr> args;

    public AstCallExpr(List<String> callee, List<AstExpr> args) {
        this.callee = callee;
        this.args = args;
    }

    public List<String> callee() {
        return callee;
    }

    public List<AstExpr> args() {
        return args;
    }
}
