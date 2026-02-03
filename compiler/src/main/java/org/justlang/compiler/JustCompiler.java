package org.justlang.compiler;

public final class JustCompiler {
    public CompileResult compile(CompileRequest request) {
        java.util.List<Diagnostic> diagnostics = new java.util.ArrayList<>();
        SourceLoader loader = new SourceLoader();
        java.util.List<SourceFile> sources = new java.util.ArrayList<>();
        java.nio.file.Path inputPath = request.inputPath();

        try {
            if (java.nio.file.Files.isDirectory(inputPath)) {
                Project project = new Project(inputPath);
                sources.addAll(loader.load(project));
            } else {
                sources.add(loader.loadFile(inputPath));
            }
        } catch (RuntimeException error) {
            diagnostics.add(new Diagnostic(error.getMessage(), inputPath));
            return new CompileResult(false, diagnostics);
        }

        if (sources.isEmpty()) {
            diagnostics.add(new Diagnostic("No .just sources found.", inputPath));
            return new CompileResult(false, diagnostics);
        }

        Lexer lexer = new Lexer();
        Parser parser = new Parser();
        TypeChecker typeChecker = new TypeChecker();
        java.util.List<AstItem> items = new java.util.ArrayList<>();
        boolean success = true;

        for (SourceFile source : sources) {
            try {
                java.util.List<Token> tokens = lexer.lex(source);
                AstModule module = parser.parse(tokens);
                TypeResult typeResult = typeChecker.typeCheck(module);
                if (!typeResult.success()) {
                    for (String error : typeResult.environment().errors()) {
                        diagnostics.add(new Diagnostic(error, source.path()));
                    }
                    success = false;
                }
                items.addAll(module.items());
            } catch (RuntimeException error) {
                diagnostics.add(new Diagnostic(error.getMessage(), source.path()));
                success = false;
            }
        }

        if (!success) {
            return new CompileResult(false, diagnostics);
        }

        if (!request.emitJar()) {
            diagnostics.add(new Diagnostic(
                "Checked " + sources.size() + " source file(s).",
                inputPath
            ));
            return new CompileResult(true, diagnostics);
        }

        Codegen codegen = new Codegen();
        java.util.List<ClassFile> classFiles;
        try {
            classFiles = codegen.emit(new AstModule(items));
        } catch (RuntimeException error) {
            diagnostics.add(new Diagnostic("Codegen error: " + error.getMessage(), inputPath));
            return new CompileResult(false, diagnostics);
        }

        JarEmitter jarEmitter = new JarEmitter();
        try {
            jarEmitter.writeJar(classFiles, request.outputJar(), codegen.mainClassName());
            diagnostics.add(new Diagnostic(
                "Compiled " + sources.size() + " source file(s).",
                request.outputJar()
            ));
            return new CompileResult(true, diagnostics);
        } catch (java.io.IOException error) {
            diagnostics.add(new Diagnostic(
                "Failed to write jar: " + error.getMessage(),
                request.outputJar()
            ));
            return new CompileResult(false, diagnostics);
        }
    }
}
