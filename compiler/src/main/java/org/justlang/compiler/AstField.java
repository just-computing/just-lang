package org.justlang.compiler;

public final class AstField {
    private final String name;
    private final String type;

    public AstField(String name, String type) {
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
