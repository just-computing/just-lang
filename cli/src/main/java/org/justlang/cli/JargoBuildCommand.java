package org.justlang.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import org.justlang.compiler.CompileResult;

public final class JargoBuildCommand implements Command {
    private final Path projectRoot;

    public JargoBuildCommand(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public int run() {
        Path manifest = projectRoot.resolve("just.toml");
        if (!Files.exists(manifest)) {
            System.err.println("Missing just.toml in " + projectRoot);
            return 2;
        }
        Path outputJar = projectRoot.resolve("build/just.jar");
        ProjectLoader loader = new ProjectLoader();
        ProjectConfig config;
        try {
            config = loader.load(projectRoot);
        } catch (RuntimeException error) {
            System.err.println(error.getMessage());
            return 2;
        }
        CompilerService compilerService = new CompilerService();
        CompileResult result = compilerService.build(config, outputJar);
        for (var diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.message());
        }
        if (!result.success()) {
            return 1;
        }
        System.out.println("Wrote " + outputJar);
        return 0;
    }
}
