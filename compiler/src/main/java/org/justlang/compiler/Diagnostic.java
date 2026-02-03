package org.justlang.compiler;

import java.nio.file.Path;

public final class Diagnostic {
    private final String message;
    private final Path path;

    public Diagnostic(String message, Path path) {
        this.message = message;
        this.path = path;
    }

    public String message() {
        return message;
    }

    public Path path() {
        return path;
    }
}
