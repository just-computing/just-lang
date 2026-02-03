package org.justlang.compiler;

import java.util.List;

public final class AstStructInitExpr implements AstExpr {
    private final String name;
    private final List<AstFieldInit> fields;

    public AstStructInitExpr(String name, List<AstFieldInit> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String name() {
        return name;
    }

    public List<AstFieldInit> fields() {
        return fields;
    }
}
