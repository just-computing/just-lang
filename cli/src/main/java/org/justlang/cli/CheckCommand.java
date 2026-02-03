package org.justlang.cli;

import java.nio.file.Path;

public final class CheckCommand implements Command {
    private final Path inputPath;

    public CheckCommand(Path inputPath) {
        this.inputPath = inputPath;
    }

    @Override
    public int run() {
        System.err.println("check is not implemented yet for " + inputPath);
        return 2;
    }
}
