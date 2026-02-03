package org.justlang.compiler;

import java.util.List;

public final class AstBlockExpr implements AstExpr {
    private final List<AstStmt> statements;
    private final AstExpr value;

    public AstBlockExpr(List<AstStmt> statements, AstExpr value) {
        this.statements = statements;
        this.value = value;
    }

    public List<AstStmt> statements() {
        return statements;
    }

    public AstExpr value() {
        return value;
    }
}
