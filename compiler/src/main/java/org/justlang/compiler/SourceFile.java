package org.justlang.compiler;

import java.nio.file.Path;

public final class SourceFile {
    private final Path path;
    private final String contents;

    public SourceFile(Path path, String contents) {
        this.path = path;
        this.contents = contents;
    }

    public Path path() {
        return path;
    }

    public String contents() {
        return contents;
    }
}
