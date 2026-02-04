package org.justlang.compiler;

public final class AstParam {
    private final String name;
    private final String type;
    private final boolean mutable;

    public AstParam(String name, String type, boolean mutable) {
        this.name = name;
        this.type = type;
        this.mutable = mutable;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public boolean mutable() {
        return mutable;
    }
}
