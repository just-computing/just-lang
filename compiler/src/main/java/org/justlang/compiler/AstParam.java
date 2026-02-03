package org.justlang.compiler;

public final class AstParam {
    private final String name;

    public AstParam(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
