package org.justlang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProjectLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void directoryWithManifestResolvesMainEntrypoint() throws IOException {
        Path project = tempDir.resolve("app");
        Path src = project.resolve("src");
        Files.createDirectories(src);
        Files.writeString(project.resolve("just.toml"), """
            name = "app"
            main = "src/main.just"
            """);
        Path main = src.resolve("main.just").toAbsolutePath().normalize();
        Files.writeString(main, "fn main() { return; }\n");

        ProjectLoader loader = new ProjectLoader();
        ProjectConfig config = loader.load(project);

        assertEquals(main, config.inputPath());
        assertEquals(project.toAbsolutePath().normalize(), config.projectRoot());
    }

    @Test
    void fileInsideProjectLoadsDependencyRootsFromManifest() throws IOException {
        Path project = tempDir.resolve("app");
        Path src = project.resolve("src");
        Path deps = tempDir.resolve("shared-lib");
        Files.createDirectories(src);
        Files.createDirectories(deps.resolve("src"));
        Files.writeString(project.resolve("just.toml"), """
            name = "app"
            main = "src/main.just"

            [dependencies]
            shared = { path = "../shared-lib" }
            """);
        Path main = src.resolve("main.just").toAbsolutePath().normalize();
        Files.writeString(main, "fn main() { return; }\n");

        ProjectLoader loader = new ProjectLoader();
        ProjectConfig config = loader.load(main);

        assertTrue(config.dependencyRoots().containsKey("shared"));
        assertEquals(deps.resolve("src").toAbsolutePath().normalize(), config.dependencyRoots().get("shared"));
    }
}
