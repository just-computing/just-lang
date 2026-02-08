package org.justlang.compiler;

import java.nio.file.Path;
import java.util.Map;

public final class CompileRequest {
    private final Path inputPath;
    private final Path outputJar;
    private final boolean emitJar;
    private final Map<String, Path> dependencyRoots;

    public CompileRequest(Path inputPath, Path outputJar, boolean emitJar, Map<String, Path> dependencyRoots) {
        this.inputPath = inputPath;
        this.outputJar = outputJar;
        this.emitJar = emitJar;
        this.dependencyRoots = Map.copyOf(dependencyRoots);
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

    public Map<String, Path> dependencyRoots() {
        return dependencyRoots;
    }

    public static CompileRequest forBuild(Path inputPath, Path outputJar) {
        return forBuild(inputPath, outputJar, Map.of());
    }

    public static CompileRequest forBuild(Path inputPath, Path outputJar, Map<String, Path> dependencyRoots) {
        return new CompileRequest(inputPath, outputJar, true, dependencyRoots);
    }

    public static CompileRequest forCheck(Path inputPath) {
        return forCheck(inputPath, Map.of());
    }

    public static CompileRequest forCheck(Path inputPath, Map<String, Path> dependencyRoots) {
        return new CompileRequest(inputPath, null, false, dependencyRoots);
    }
}
