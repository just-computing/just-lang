package org.justlang.compiler;

public final class AstLetStmt implements AstStmt {
    private final String name;
    private final boolean mutable;
    private final String type;
    private final AstExpr initializer;

    public AstLetStmt(String name, boolean mutable, String type, AstExpr initializer) {
        this.name = name;
        this.mutable = mutable;
        this.type = type;
        this.initializer = initializer;
    }

    public String name() {
        return name;
    }

    public boolean mutable() {
        return mutable;
    }

    public String type() {
        return type;
    }

    public AstExpr initializer() {
        return initializer;
    }
}
