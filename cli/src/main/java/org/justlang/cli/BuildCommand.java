package org.justlang.cli;

import java.nio.file.Path;
import org.justlang.compiler.CompileResult;

public final class BuildCommand implements Command {
    private final Path inputPath;
    private final Path outputJar;

    public BuildCommand(Path inputPath, Path outputJar) {
        this.inputPath = inputPath;
        this.outputJar = outputJar;
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
        CompilerService compilerService = new CompilerService();
        CompileResult result = compilerService.build(config, outputJar);

        for (var diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.message());
        }

        if (result.success()) {
            System.out.println("Wrote " + outputJar);
            return 0;
        }
        return 1;
    }
}
