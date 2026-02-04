package org.justlang.compiler;

import java.util.List;

public final class AstLoopStmt implements AstStmt {
    private final String label;
    private final List<AstStmt> body;

    public AstLoopStmt(String label, List<AstStmt> body) {
        this.label = label;
        this.body = body;
    }

    public String label() {
        return label;
    }

    public List<AstStmt> body() {
        return body;
    }
}
