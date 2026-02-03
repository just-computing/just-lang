package org.justlang.compiler;

public final class AstStringExpr implements AstExpr {
    private final String literal;

    public AstStringExpr(String literal) {
        this.literal = literal;
    }

    public String literal() {
        return literal;
    }
}
