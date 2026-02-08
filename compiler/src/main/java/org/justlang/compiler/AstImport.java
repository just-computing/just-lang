package org.justlang.compiler;

public final class AstImport implements AstItem {
    private final String path;

    public AstImport(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
