package org.justlang.compiler;

public final class AstContinueStmt implements AstStmt {
    private final String label;

    public AstContinueStmt(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
