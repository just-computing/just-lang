package org.justlang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JargoNewCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void createsMultiFileProjectTemplateWithImports() throws IOException {
        Path project = tempDir.resolve("sample-app");
        JargoNewCommand command = new JargoNewCommand(project.toString());

        assertEquals(0, command.run());

        Path manifest = project.resolve("just.toml");
        Path mainFile = project.resolve("src/main.just");
        Path appFile = project.resolve("src/app.just");

        assertTrue(Files.exists(manifest));
        assertTrue(Files.exists(mainFile));
        assertTrue(Files.exists(appFile));

        String main = Files.readString(mainFile);
        assertTrue(main.contains("mod app;"));
        assertTrue(main.contains("use app::app_greeting;"));
        assertTrue(main.contains("app_greeting()"));
        String app = Files.readString(appFile);
        assertTrue(app.contains("pub fn app_greeting()"));
    }

    @Test
    void keepsExistingMainFileUnchanged() throws IOException {
        Path project = tempDir.resolve("existing-app");
        Path src = project.resolve("src");
        Files.createDirectories(src);
        Path mainFile = src.resolve("main.just");
        String customMain = "fn main() { std::print(\"custom\"); return; }\n";
        Files.writeString(mainFile, customMain);

        JargoNewCommand command = new JargoNewCommand(project.toString());
        assertEquals(0, command.run());

        assertEquals(customMain, Files.readString(mainFile));
        assertTrue(Files.exists(project.resolve("src/app.just")));
    }
}
