package org.justlang.compiler;

public final class Token {
    private final TokenKind kind;
    private final String lexeme;
    private final int line;
    private final int column;

    public Token(TokenKind kind, String lexeme, int line, int column) {
        this.kind = kind;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    public TokenKind kind() {
        return kind;
    }

    public String lexeme() {
        return lexeme;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public enum TokenKind {
        IDENT,
        NUMBER,
        STRING,
        KEYWORD,
        SYMBOL,
        EOF
    }
}
