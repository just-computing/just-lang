package org.justlang.cli;

import java.nio.file.Path;
import org.justlang.compiler.CompileRequest;
import org.justlang.compiler.CompileResult;
import org.justlang.compiler.JustCompiler;

public final class CompilerService {
    public CompileResult build(ProjectConfig config, Path outputJar) {
        JustCompiler compiler = new JustCompiler();
        CompileRequest request = CompileRequest.forBuild(config.inputPath(), outputJar, config.dependencyRoots());
        return compiler.compile(request);
    }

    public CompileResult check(ProjectConfig config) {
        JustCompiler compiler = new JustCompiler();
        CompileRequest request = CompileRequest.forCheck(config.inputPath(), config.dependencyRoots());
        return compiler.compile(request);
    }
}
