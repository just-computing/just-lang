package org.justlang.compiler;

import java.nio.file.Path;

public final class CompileRequest {
    private final Path inputPath;
    private final Path outputJar;
    private final boolean emitJar;

    public CompileRequest(Path inputPath, Path outputJar, boolean emitJar) {
        this.inputPath = inputPath;
        this.outputJar = outputJar;
        this.emitJar = emitJar;
    }

    public Path inputPath() {
        return inputPath;
    }

    public Path outputJar() {
        return outputJar;
    }

    public boolean emitJar() {
        return emitJar;
    }

    public static CompileRequest forBuild(Path inputPath, Path outputJar) {
        return new CompileRequest(inputPath, outputJar, true);
    }

    public static CompileRequest forCheck(Path inputPath) {
        return new CompileRequest(inputPath, null, false);
    }
}
