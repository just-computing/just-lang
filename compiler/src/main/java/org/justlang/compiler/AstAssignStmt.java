package org.justlang.compiler;

public final class AstAssignStmt implements AstStmt {
    private final String name;
    private final String operator;
    private final AstExpr value;

    public AstAssignStmt(String name, String operator, AstExpr value) {
        this.name = name;
        this.operator = operator;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String operator() {
        return operator;
    }

    public AstExpr value() {
        return value;
    }
}
