package org.justlang.compiler;

import java.util.List;

public final class AstIfStmt implements AstStmt {
    private final AstExpr condition;
    private final List<AstStmt> thenBranch;
    private final List<AstStmt> elseBranch;

    public AstIfStmt(AstExpr condition, List<AstStmt> thenBranch, List<AstStmt> elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public AstExpr condition() {
        return condition;
    }

    public List<AstStmt> thenBranch() {
        return thenBranch;
    }

    public List<AstStmt> elseBranch() {
        return elseBranch;
    }
}
