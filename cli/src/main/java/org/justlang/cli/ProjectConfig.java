package org.justlang.cli;

import java.nio.file.Path;

public final class ProjectConfig {
    private final Path inputPath;

    public ProjectConfig(Path inputPath) {
        this.inputPath = inputPath;
    }

    public Path inputPath() {
        return inputPath;
    }
}
