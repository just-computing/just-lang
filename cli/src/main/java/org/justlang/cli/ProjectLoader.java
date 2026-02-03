package org.justlang.cli;

import java.nio.file.Path;

public final class ProjectLoader {
    public ProjectConfig load(Path inputPath) {
        return new ProjectConfig(inputPath);
    }
}
