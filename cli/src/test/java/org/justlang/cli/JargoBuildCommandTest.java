package org.justlang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JargoBuildCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsProjectUsingManifestEntrypointAndDependencyAliasImports() throws IOException {
        Path dep = tempDir.resolve("shared-lib");
        Path depSrc = dep.resolve("src");
        Files.createDirectories(depSrc);
        Files.writeString(dep.resolve("just.toml"), """
            name = "shared-lib"
            main = "src/math.just"
            """);
        Files.writeString(depSrc.resolve("math.just"), """
            pub fn double_value(x: i32) -> i32 {
                return x * 2;
            }
            """);

        Path app = tempDir.resolve("app");
        Path appSrc = app.resolve("src");
        Files.createDirectories(appSrc);
        Files.writeString(app.resolve("just.toml"), """
            name = "app"
            main = "src/main.just"

            [dependencies]
            shared = { path = "../shared-lib" }
            """);
        Files.writeString(appSrc.resolve("main.just"), """
            import "@shared/math.just";
            use math::double_value;

            fn main() {
                std::print(double_value(4));
                return;
            }
            """);

        JargoBuildCommand command = new JargoBuildCommand(app);
        int exitCode = command.run();

        assertEquals(0, exitCode);
        assertTrue(Files.exists(app.resolve("build/just.jar")));
    }
}
