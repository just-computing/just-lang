package org.justlang.compiler;

public final class JustCompiler {
    private final LexerStrategy lexer;
    private final ParserStrategy parser;
    private final TypeCheckerStrategy typeChecker;
    private final CodegenStrategy codegen;
    private final JarEmitter jarEmitter;

    public JustCompiler() {
        this(new Lexer(), new Parser(), new TypeChecker(), new Codegen(), new JarEmitter());
    }

    public JustCompiler(
        LexerStrategy lexer,
        ParserStrategy parser,
        TypeCheckerStrategy typeChecker,
        CodegenStrategy codegen,
        JarEmitter jarEmitter
    ) {
        this.lexer = lexer;
        this.parser = parser;
        this.typeChecker = typeChecker;
        this.codegen = codegen;
        this.jarEmitter = jarEmitter;
    }

    public CompileResult compile(CompileRequest request) {
        Diagnostics diagnostics = new Diagnostics();
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
            diagnostics.report(new Diagnostic(error.getMessage(), inputPath));
            return new CompileResult(false, diagnostics.all());
        }

        if (sources.isEmpty()) {
            diagnostics.report(new Diagnostic("No .just sources found.", inputPath));
            return new CompileResult(false, diagnostics.all());
        }

        java.util.List<AstItem> items = new java.util.ArrayList<>();
        boolean success = true;

        for (SourceFile source : sources) {
            try {
                java.util.List<Token> tokens = lexer.lex(source, diagnostics);
                AstModule module = parser.parse(source, tokens, diagnostics);
                TypeResult typeResult = typeChecker.typeCheck(module);
                for (String warning : typeResult.environment().warnings()) {
                    diagnostics.report(new Diagnostic("warning: " + warning, source.path()));
                }
                if (!typeResult.success()) {
                    for (String error : typeResult.environment().errors()) {
                        diagnostics.report(new Diagnostic(error, source.path()));
                    }
                    success = false;
                }
                items.addAll(module.items());
            } catch (LexException | ParseException error) {
                success = false;
            } catch (RuntimeException error) {
                diagnostics.report(new Diagnostic(error.getMessage(), source.path()));
                success = false;
            }
        }

        if (!success) {
            return new CompileResult(false, diagnostics.all());
        }

        if (!request.emitJar()) {
            diagnostics.report(new Diagnostic(
                "Checked " + sources.size() + " source file(s).",
                inputPath
            ));
            return new CompileResult(true, diagnostics.all());
        }

        java.util.List<ClassFile> classFiles;
        try {
            classFiles = codegen.emit(new AstModule(items));
        } catch (RuntimeException error) {
            diagnostics.report(new Diagnostic("Codegen error: " + error.getMessage(), inputPath));
            return new CompileResult(false, diagnostics.all());
        }

        try {
            jarEmitter.writeJar(classFiles, request.outputJar(), codegen.mainClassName());
            diagnostics.report(new Diagnostic(
                "Compiled " + sources.size() + " source file(s).",
                request.outputJar()
            ));
            return new CompileResult(true, diagnostics.all());
        } catch (java.io.IOException error) {
            diagnostics.report(new Diagnostic(
                "Failed to write jar: " + error.getMessage(),
                request.outputJar()
            ));
            return new CompileResult(false, diagnostics.all());
        }
    }
}
