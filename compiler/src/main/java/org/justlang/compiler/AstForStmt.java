package org.justlang.compiler;

import java.util.List;

public final class AstForStmt implements AstStmt {
    private final String name;
    private final AstExpr start;
    private final AstExpr end;
    private final boolean inclusive;
    private final List<AstStmt> body;

    public AstForStmt(String name, AstExpr start, AstExpr end, boolean inclusive, List<AstStmt> body) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.inclusive = inclusive;
        this.body = body;
    }

    public String name() {
        return name;
    }

    public AstExpr start() {
        return start;
    }

    public AstExpr end() {
        return end;
    }

    public boolean inclusive() {
        return inclusive;
    }

    public List<AstStmt> body() {
        return body;
    }
}
