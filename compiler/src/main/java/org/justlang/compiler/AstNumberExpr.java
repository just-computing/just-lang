package org.justlang.compiler;

public final class AstNumberExpr implements AstExpr {
    private final String literal;

    public AstNumberExpr(String literal) {
        this.literal = literal;
    }

    public String literal() {
        return literal;
    }
}
