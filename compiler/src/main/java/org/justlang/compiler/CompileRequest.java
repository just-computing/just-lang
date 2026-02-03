package org.justlang.compiler;

import java.nio.file.Path;

public final class CompileRequest {
    private final Path inputPath;
    private final Path outputJar;

    public CompileRequest(Path inputPath, Path outputJar) {
        this.inputPath = inputPath;
        this.outputJar = outputJar;
    }

    public Path inputPath() {
        return inputPath;
    }

    public Path outputJar() {
        return outputJar;
    }
}
