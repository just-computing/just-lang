package org.justlang.compiler;

public interface ParserStrategy {
    AstModule parse(SourceFile sourceFile, java.util.List<Token> tokens, Diagnostics diagnostics);
}
