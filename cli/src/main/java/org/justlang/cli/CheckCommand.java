package org.justlang.cli;

import java.nio.file.Path;

public final class CheckCommand implements Command {
    private final Path inputPath;

    public CheckCommand(Path inputPath) {
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
        CompilerService compilerService = new CompilerService();
        var result = compilerService.check(config);
        for (var diagnostic : result.diagnostics()) {
            System.err.println(diagnostic.message());
        }
        return result.success() ? 0 : 1;
    }
}
