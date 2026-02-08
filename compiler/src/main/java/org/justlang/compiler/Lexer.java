package org.justlang.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Lexer implements LexerStrategy {
    private static final Set<String> KEYWORDS = Set.of(
        "fn",
        "let",
        "mut",
        "return",
        "enum",
        "struct",
        "true",
        "false",
        "if",
        "else",
        "while",
        "for",
        "in",
        "loop",
        "match",
        "break",
        "continue",
        "import"
    );

    @Override
    public List<Token> lex(SourceFile sourceFile, Diagnostics diagnostics) {
        String source = sourceFile.contents();
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        int line = 1;
        int column = 1;

        while (index < source.length()) {
            char c = source.charAt(index);

            if (c == '\n') {
                index++;
                line++;
                column = 1;
                continue;
            }

            if (Character.isWhitespace(c)) {
                index++;
                column++;
                continue;
            }

            if (c == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                index += 2;
                column += 2;
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                    column++;
                }
                continue;
            }

            if (isIdentStart(c)) {
                int start = index;
                int startColumn = column;
                index++;
                column++;
                while (index < source.length() && isIdentPart(source.charAt(index))) {
                    index++;
                    column++;
                }
                String text = source.substring(start, index);
                Token.TokenKind kind = KEYWORDS.contains(text) ? Token.TokenKind.KEYWORD : Token.TokenKind.IDENT;
                tokens.add(new Token(kind, text, line, startColumn));
                continue;
            }

            if (Character.isDigit(c)) {
                int start = index;
                int startColumn = column;
                index++;
                column++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                    column++;
                }
                String text = source.substring(start, index);
                tokens.add(new Token(Token.TokenKind.NUMBER, text, line, startColumn));
                continue;
            }

            if (c == '"') {
                int startColumn = column;
                index++;
                column++;
                StringBuilder value = new StringBuilder();
                while (index < source.length()) {
                    char ch = source.charAt(index);
                    if (ch == '"') {
                        index++;
                        column++;
                        break;
                    }
                    if (ch == '\\' && index + 1 < source.length()) {
                        char next = source.charAt(index + 1);
                        if (next == 'n') {
                            value.append('\n');
                            index += 2;
                            column += 2;
                            continue;
                        }
                        if (next == '"' || next == '\\') {
                            value.append(next);
                            index += 2;
                            column += 2;
                            continue;
                        }
                    }
                    value.append(ch);
                    index++;
                    column++;
                }
                tokens.add(new Token(Token.TokenKind.STRING, value.toString(), line, startColumn));
                continue;
            }

            if (isSymbolStart(c)) {
                int startColumn = column;
                String symbol = null;
                if (index + 1 < source.length()) {
                    char next = source.charAt(index + 1);
                    if (c == '=' && next == '>') {
                        symbol = "=>";
                    } else if (c == '.' && next == '.') {
                        if (index + 2 < source.length() && source.charAt(index + 2) == '=') {
                            symbol = "..=";
                        } else {
                            symbol = "..";
                        }
                    } else if ((c == '=' || c == '!' || c == '<' || c == '>') && next == '=') {
                        symbol = "" + c + next;
                    } else if ((c == '+' || c == '-' || c == '*' || c == '/') && next == '=') {
                        symbol = "" + c + next;
                    } else if (c == '-' && next == '>') {
                        symbol = "->";
                    } else if (c == '&' && next == '&') {
                        symbol = "&&";
                    } else if (c == '|' && next == '|') {
                        symbol = "||";
                    }
                }
                if (symbol != null) {
                    tokens.add(new Token(Token.TokenKind.SYMBOL, symbol, line, startColumn));
                    int advance = symbol.length();
                    index += advance;
                    column += advance;
                } else {
                    tokens.add(new Token(Token.TokenKind.SYMBOL, java.lang.String.valueOf(c), line, startColumn));
                    index++;
                    column++;
                }
                continue;
            }

            String message = "Unexpected character '" + c + "' at " + line + ":" + column;
            diagnostics.report(new Diagnostic(message, sourceFile.path()));
            throw new LexException(message);
        }

        tokens.add(new Token(Token.TokenKind.EOF, "", line, column));
        return tokens;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isSymbolStart(char c) {
        return "(){}[],;=:+-*/&<>.!|'".indexOf(c) >= 0;
    }
}
