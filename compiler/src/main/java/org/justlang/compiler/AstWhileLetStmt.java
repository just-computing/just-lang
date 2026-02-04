package org.justlang.compiler;

import java.util.List;

public final class AstWhileLetStmt implements AstStmt {
    private final String label;
    private final AstMatchPattern pattern;
    private final AstExpr target;
    private final List<AstStmt> body;

    public AstWhileLetStmt(String label, AstMatchPattern pattern, AstExpr target, List<AstStmt> body) {
        this.label = label;
        this.pattern = pattern;
        this.target = target;
        this.body = body;
    }

    public String label() {
        return label;
    }

    public AstMatchPattern pattern() {
        return pattern;
    }

    public AstExpr target() {
        return target;
    }

    public List<AstStmt> body() {
        return body;
    }
}
