package org.justlang.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ProjectLoader {
    public ProjectConfig load(Path inputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedInput)) {
            Path manifestPath = normalizedInput.resolve("just.toml");
            if (Files.exists(manifestPath)) {
                ProjectManifest manifest = loadManifest(manifestPath);
                String mainValue = manifest.main() == null ? "src/main.just" : manifest.main();
                Path entry = normalizedInput.resolve(mainValue).normalize();
                if (!Files.exists(entry)) {
                    throw new IllegalArgumentException("Missing main file declared in just.toml: " + entry);
                }
                return new ProjectConfig(entry, normalizedInput, manifest.dependencyRoots(normalizedInput));
            }
            return new ProjectConfig(normalizedInput, normalizedInput, Map.of());
        }

        Path projectRoot = findProjectRoot(normalizedInput);
        Map<String, Path> dependencies = Map.of();
        if (projectRoot != null) {
            Path manifestPath = projectRoot.resolve("just.toml");
            if (Files.exists(manifestPath)) {
                dependencies = loadManifest(manifestPath).dependencyRoots(projectRoot);
            }
        }
        return new ProjectConfig(normalizedInput, projectRoot != null ? projectRoot : normalizedInput.getParent(), dependencies);
    }

    private ProjectManifest loadManifest(Path manifestPath) {
        try {
            return ProjectManifest.load(manifestPath);
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to read just.toml: " + error.getMessage(), error);
        }
    }

    private Path findProjectRoot(Path filePath) {
        Path current = filePath.getParent();
        while (current != null) {
            if (Files.exists(current.resolve("just.toml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
