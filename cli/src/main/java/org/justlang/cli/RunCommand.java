package org.justlang.cli;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RunCommand implements Command {
    private final Path inputPath;

    public RunCommand(Path inputPath) {
        this.inputPath = inputPath;
    }

    @Override
    public int run() {
        ProjectLoader loader = new ProjectLoader();
        ProjectConfig config;
        try {
            config = loader.load(inputPath);
        } catch (RuntimeException error) {
            System.err.println(error.getMessage());
            return 2;
        }
        Path base = config.projectRoot() != null
            ? config.projectRoot()
            : (Files.isDirectory(inputPath) ? inputPath : inputPath.getParent());
        Path outputJar = base.resolve("build/just.jar");
        CompilerService compilerService = new CompilerService();
        var result = compilerService.build(config, outputJar);
        for (var diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.message());
        }
        if (!result.success()) {
            return 1;
        }
        JarRunner runner = new JarRunner();
        return runner.runJar(outputJar);
    }
}
