package org.justlang.compiler;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private List<Token> tokens;
    private int current;

    public AstModule parse(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
        List<AstItem> items = new ArrayList<>();
        while (!isAtEnd()) {
            items.add(parseItem());
        }
        return new AstModule(items);
    }

    private AstItem parseItem() {
        if (matchKeyword("fn")) {
            return parseFunction();
        }
        if (matchKeyword("struct")) {
            return parseStruct();
        }
        throw error(peek(), "Expected item (e.g., 'fn' or 'struct')");
    }

    private AstFunction parseFunction() {
        Token name = expect(Token.TokenKind.IDENT, "Expected function name");
        expectSymbol("(");
        List<AstParam> params = new ArrayList<>();
        if (!checkSymbol(")")) {
            do {
                Token paramName = expect(Token.TokenKind.IDENT, "Expected parameter name");
                params.add(new AstParam(paramName.lexeme()));
            } while (matchSymbol(","));
        }
        expectSymbol(")");
        expectSymbol("{");
        List<AstStmt> body = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            body.add(parseStatement());
        }
        expectSymbol("}");
        return new AstFunction(name.lexeme(), params, body);
    }

    private AstStmt parseStatement() {
        if (matchKeyword("let")) {
            return parseLet();
        }
        AstExpr expr = parseExpr();
        expectSymbol(";");
        return new AstExprStmt(expr);
    }

    private AstStmt parseLet() {
        boolean mutable = matchKeyword("mut");
        Token name = expect(Token.TokenKind.IDENT, "Expected identifier after 'let'");
        AstExpr initializer = null;
        if (matchSymbol("=")) {
            initializer = parseExpr();
        }
        expectSymbol(";");
        return new AstLetStmt(name.lexeme(), mutable, initializer);
    }

    private AstExpr parseExpr() {
        return parsePrimary();
    }

    private AstExpr parsePrimary() {
        if (matchKeyword("true")) {
            return new AstBoolExpr(true);
        }
        if (matchKeyword("false")) {
            return new AstBoolExpr(false);
        }
        if (match(Token.TokenKind.NUMBER)) {
            return new AstNumberExpr(previous().lexeme());
        }
        if (match(Token.TokenKind.STRING)) {
            return new AstStringExpr(previous().lexeme());
        }
        if (check(Token.TokenKind.IDENT)) {
            List<String> path = parsePath();
            if (matchSymbol("(")) {
                List<AstExpr> args = new ArrayList<>();
                if (!checkSymbol(")")) {
                    do {
                        args.add(parseExpr());
                    } while (matchSymbol(","));
                }
                expectSymbol(")");
                return new AstCallExpr(path, args);
            }
            if (path.size() == 1) {
                return new AstIdentExpr(path.get(0));
            }
            return new AstPathExpr(path);
        }
        throw error(peek(), "Expected expression");
    }

    private AstStruct parseStruct() {
        Token name = expect(Token.TokenKind.IDENT, "Expected struct name");
        expectSymbol("{");
        List<AstField> fields = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            Token fieldName = expect(Token.TokenKind.IDENT, "Expected field name");
            expectSymbol(":");
            Token fieldType = expect(Token.TokenKind.IDENT, "Expected field type");
            fields.add(new AstField(fieldName.lexeme(), fieldType.lexeme()));
            if (!matchSymbol(",")) {
                break;
            }
        }
        expectSymbol("}");
        return new AstStruct(name.lexeme(), fields);
    }

    private List<String> parsePath() {
        List<String> segments = new ArrayList<>();
        Token first = expect(Token.TokenKind.IDENT, "Expected identifier");
        segments.add(first.lexeme());
        while (matchDoubleColon()) {
            Token segment = expect(Token.TokenKind.IDENT, "Expected identifier after '::'");
            segments.add(segment.lexeme());
        }
        return segments;
    }

    private boolean match(Token.TokenKind kind) {
        if (check(kind)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        if (check(Token.TokenKind.KEYWORD) && peek().lexeme().equals(keyword)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchSymbol(String symbol) {
        if (check(Token.TokenKind.SYMBOL) && peek().lexeme().equals(symbol)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(Token.TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private void expectSymbol(String symbol) {
        if (!matchSymbol(symbol)) {
            throw error(peek(), "Expected '" + symbol + "'");
        }
    }

    private boolean check(Token.TokenKind kind) {
        if (isAtEnd()) {
            return false;
        }
        return peek().kind() == kind;
    }

    private boolean matchDoubleColon() {
        if (!checkSymbol(":")) {
            return false;
        }
        if (current + 1 >= tokens.size()) {
            return false;
        }
        if (!tokens.get(current + 1).lexeme().equals(":")) {
            return false;
        }
        advance();
        advance();
        return true;
    }

    private boolean checkSymbol(String symbol) {
        if (check(Token.TokenKind.SYMBOL)) {
            return peek().lexeme().equals(symbol);
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().kind() == Token.TokenKind.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseException error(Token token, String message) {
        return new ParseException(message + " at " + token.line() + ":" + token.column());
    }
}
