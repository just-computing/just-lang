package org.justlang.compiler;

import java.nio.file.Path;

public final class Project {
    private final Path root;

    public Project(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }
}
