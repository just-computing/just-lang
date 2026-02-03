package org.justlang.compiler;

import java.util.List;

public final class AstWhileStmt implements AstStmt {
    private final AstExpr condition;
    private final List<AstStmt> body;

    public AstWhileStmt(AstExpr condition, List<AstStmt> body) {
        this.condition = condition;
        this.body = body;
    }

    public AstExpr condition() {
        return condition;
    }

    public List<AstStmt> body() {
        return body;
    }
}
