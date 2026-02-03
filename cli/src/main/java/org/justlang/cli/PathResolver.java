package org.justlang.cli;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PathResolver {
    private PathResolver() {
    }

    public static Path resolveInput(String input) {
        Path path = Path.of(input);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path local = cwd.resolve(path).normalize();
        if (Files.exists(local)) {
            return local;
        }

        Path repoRoot = findRepoRoot(cwd);
        if (repoRoot != null) {
            Path repoPath = repoRoot.resolve(path).normalize();
            if (Files.exists(repoPath)) {
                return repoPath;
            }
        }

        return local;
    }

    private static Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
