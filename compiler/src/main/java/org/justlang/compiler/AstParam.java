package org.justlang.compiler;

public final class AstParam {
    private final String name;
    private final String type;

    public AstParam(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }
}
