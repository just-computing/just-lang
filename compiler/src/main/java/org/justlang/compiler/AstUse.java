package org.justlang.compiler;

import java.nio.file.Path;

public final class AstUse implements AstItem {
    private final String moduleName;
    private final String symbolName;
    private final String alias;
    private final Path sourcePath;

    public AstUse(String moduleName, String symbolName, String alias, Path sourcePath) {
        this.moduleName = moduleName;
        this.symbolName = symbolName;
        this.alias = alias;
        this.sourcePath = sourcePath;
    }

    public String moduleName() {
        return moduleName;
    }

    public String symbolName() {
        return symbolName;
    }

    public String alias() {
        return alias;
    }

    public Path sourcePath() {
        return sourcePath;
    }
}
