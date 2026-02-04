package org.justlang.compiler;

import java.util.List;

public final class AstLoopExpr implements AstExpr {
    private final List<AstStmt> body;

    public AstLoopExpr(List<AstStmt> body) {
        this.body = body;
    }

    public List<AstStmt> body() {
        return body;
    }
}
