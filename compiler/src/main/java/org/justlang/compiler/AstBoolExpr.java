package org.justlang.compiler;

public final class AstBoolExpr implements AstExpr {
    private final boolean value;

    public AstBoolExpr(boolean value) {
        this.value = value;
    }

    public boolean value() {
        return value;
    }
}
