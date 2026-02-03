package org.justlang.compiler;

import java.util.List;

public final class AstFunction implements AstItem {
    private final String name;
    private final List<AstParam> params;
    private final List<AstStmt> body;

    public AstFunction(String name, List<AstParam> params, List<AstStmt> body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    public String name() {
        return name;
    }

    public List<AstParam> params() {
        return params;
    }

    public List<AstStmt> body() {
        return body;
    }
}
