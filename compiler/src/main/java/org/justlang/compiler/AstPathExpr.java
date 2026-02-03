package org.justlang.compiler;

import java.util.List;

public final class AstPathExpr implements AstExpr {
    private final List<String> segments;

    public AstPathExpr(List<String> segments) {
        this.segments = segments;
    }

    public List<String> segments() {
        return segments;
    }
}
