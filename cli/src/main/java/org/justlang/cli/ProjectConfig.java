package org.justlang.cli;

import java.nio.file.Path;
import java.util.Map;

public final class ProjectConfig {
    private final Path inputPath;
    private final Path projectRoot;
    private final Map<String, Path> dependencyRoots;

    public ProjectConfig(Path inputPath, Path projectRoot, Map<String, Path> dependencyRoots) {
        this.inputPath = inputPath;
        this.projectRoot = projectRoot;
        this.dependencyRoots = Map.copyOf(dependencyRoots);
    }

    public Path inputPath() {
        return inputPath;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Map<String, Path> dependencyRoots() {
        return dependencyRoots;
    }
}
