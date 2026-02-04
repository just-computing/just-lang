package org.justlang.compiler;

import java.util.List;

public interface LexerStrategy {
    List<Token> lex(SourceFile sourceFile, Diagnostics diagnostics);
}
