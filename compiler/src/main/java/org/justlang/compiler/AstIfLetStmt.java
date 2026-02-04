package org.justlang.compiler;

import java.util.List;

public final class AstIfLetStmt implements AstStmt {
    private final AstMatchPattern pattern;
    private final AstExpr target;
    private final List<AstStmt> thenBranch;
    private final List<AstStmt> elseBranch;

    public AstIfLetStmt(AstMatchPattern pattern, AstExpr target, List<AstStmt> thenBranch, List<AstStmt> elseBranch) {
        this.pattern = pattern;
        this.target = target;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public AstMatchPattern pattern() {
        return pattern;
    }

    public AstExpr target() {
        return target;
    }

    public List<AstStmt> thenBranch() {
        return thenBranch;
    }

    public List<AstStmt> elseBranch() {
        return elseBranch;
    }
}
