package org.justlang.compiler;

import java.nio.file.Path;
import java.util.List;

public final class AstFunction implements AstItem {
    private final String name;
    private final List<AstParam> params;
    private final String returnType;
    private final List<AstStmt> body;
    private final boolean publicItem;
    private final Path sourcePath;

    public AstFunction(String name, List<AstParam> params, String returnType, List<AstStmt> body) {
        this(name, params, returnType, body, false, null);
    }

    public AstFunction(
        String name,
        List<AstParam> params,
        String returnType,
        List<AstStmt> body,
        boolean publicItem,
        Path sourcePath
    ) {
        this.name = name;
        this.params = params;
        this.returnType = returnType;
        this.body = body;
        this.publicItem = publicItem;
        this.sourcePath = sourcePath;
    }

    public String name() {
        return name;
    }

    public List<AstParam> params() {
        return params;
    }

    public String returnType() {
        return returnType;
    }

    public List<AstStmt> body() {
        return body;
    }

    public boolean isPublicItem() {
        return publicItem;
    }

    public Path sourcePath() {
        return sourcePath;
    }
}
