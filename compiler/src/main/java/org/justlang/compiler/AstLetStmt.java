package org.justlang.compiler;

public final class AstLetStmt implements AstStmt {
    private final String name;
    private final boolean mutable;
    private final AstExpr initializer;

    public AstLetStmt(String name, boolean mutable, AstExpr initializer) {
        this.name = name;
        this.mutable = mutable;
        this.initializer = initializer;
    }

    public String name() {
        return name;
    }

    public boolean mutable() {
        return mutable;
    }

    public AstExpr initializer() {
        return initializer;
    }
}
