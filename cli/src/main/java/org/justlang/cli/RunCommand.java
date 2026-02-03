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
        Path base = Files.isDirectory(inputPath) ? inputPath : inputPath.getParent();
        Path outputJar = base.resolve("build/just.jar");
        ProjectLoader loader = new ProjectLoader();
        ProjectConfig config = loader.load(inputPath);
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
