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
                expectSymbol(":");
                String paramType = parseTypeName();
                params.add(new AstParam(paramName.lexeme(), paramType));
            } while (matchSymbol(","));
        }
        expectSymbol(")");
        String returnType = null;
        if (matchSymbol("->")) {
            returnType = parseTypeName();
        }
        List<AstStmt> body = parseBlock();
        return new AstFunction(name.lexeme(), params, returnType, body);
    }

    private AstStmt parseStatement() {
        if (matchKeyword("if")) {
            return parseIf();
        }
        if (matchKeyword("while")) {
            return parseWhile();
        }
        if (matchKeyword("break")) {
            expectSymbol(";");
            return new AstBreakStmt();
        }
        if (matchKeyword("continue")) {
            expectSymbol(";");
            return new AstContinueStmt();
        }
        if (matchKeyword("return")) {
            return parseReturn();
        }
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
        return parseOr();
    }

    private AstExpr parseOr() {
        AstExpr expr = parseAnd();
        while (true) {
            if (matchSymbol("||")) {
                AstExpr right = parseAnd();
                expr = new AstBinaryExpr(expr, "||", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseAnd() {
        AstExpr expr = parseEquality();
        while (true) {
            if (matchSymbol("&&")) {
                AstExpr right = parseEquality();
                expr = new AstBinaryExpr(expr, "&&", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseEquality() {
        AstExpr expr = parseComparison();
        while (true) {
            if (matchSymbol("==")) {
                AstExpr right = parseComparison();
                expr = new AstBinaryExpr(expr, "==", right);
                continue;
            }
            if (matchSymbol("!=")) {
                AstExpr right = parseComparison();
                expr = new AstBinaryExpr(expr, "!=", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseComparison() {
        AstExpr expr = parseTerm();
        while (true) {
            if (matchSymbol("<")) {
                AstExpr right = parseTerm();
                expr = new AstBinaryExpr(expr, "<", right);
                continue;
            }
            if (matchSymbol("<=")) {
                AstExpr right = parseTerm();
                expr = new AstBinaryExpr(expr, "<=", right);
                continue;
            }
            if (matchSymbol(">")) {
                AstExpr right = parseTerm();
                expr = new AstBinaryExpr(expr, ">", right);
                continue;
            }
            if (matchSymbol(">=")) {
                AstExpr right = parseTerm();
                expr = new AstBinaryExpr(expr, ">=", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseTerm() {
        AstExpr expr = parseFactor();
        while (true) {
            if (matchSymbol("+")) {
                AstExpr right = parseFactor();
                expr = new AstBinaryExpr(expr, "+", right);
                continue;
            }
            if (matchSymbol("-")) {
                AstExpr right = parseFactor();
                expr = new AstBinaryExpr(expr, "-", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseFactor() {
        AstExpr expr = parseUnary();
        while (true) {
            if (matchSymbol("*")) {
                AstExpr right = parseUnary();
                expr = new AstBinaryExpr(expr, "*", right);
                continue;
            }
            if (matchSymbol("/")) {
                AstExpr right = parseUnary();
                expr = new AstBinaryExpr(expr, "/", right);
                continue;
            }
            return expr;
        }
    }

    private AstExpr parseUnary() {
        if (matchSymbol("!")) {
            AstExpr right = parseUnary();
            return new AstUnaryExpr("!", right);
        }
        if (matchSymbol("-")) {
            AstExpr right = parseUnary();
            return new AstUnaryExpr("-", right);
        }
        AstExpr expr = parsePrimary();
        while (matchSymbol(".")) {
            Token field = expect(Token.TokenKind.IDENT, "Expected field name after '.'");
            expr = new AstFieldAccessExpr(expr, field.lexeme());
        }
        return expr;
    }

    private AstExpr parsePrimary() {
        if (matchKeyword("if")) {
            return parseIfExpr();
        }
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
            if (path.size() == 1 && matchSymbol("{")) {
                List<AstFieldInit> fields = new ArrayList<>();
                if (!checkSymbol("}")) {
                    do {
                        Token fieldName = expect(Token.TokenKind.IDENT, "Expected field name");
                        expectSymbol(":");
                        AstExpr value = parseExpr();
                        fields.add(new AstFieldInit(fieldName.lexeme(), value));
                    } while (matchSymbol(","));
                }
                expectSymbol("}");
                return new AstStructInitExpr(path.get(0), fields);
            }
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
        if (matchSymbol("(")) {
            AstExpr expr = parseExpr();
            expectSymbol(")");
            return expr;
        }
        throw error(peek(), "Expected expression");
    }

    private List<AstStmt> parseBlock() {
        expectSymbol("{");
        List<AstStmt> body = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            body.add(parseStatement());
        }
        expectSymbol("}");
        return body;
    }

    private AstStmt parseIf() {
        AstExpr condition = parseExpr();
        List<AstStmt> thenBranch = parseBlock();
        List<AstStmt> elseBranch = null;
        if (matchKeyword("else")) {
            elseBranch = parseBlock();
        }
        return new AstIfStmt(condition, thenBranch, elseBranch);
    }

    private AstStmt parseWhile() {
        AstExpr condition = parseExpr();
        List<AstStmt> body = parseBlock();
        return new AstWhileStmt(condition, body);
    }

    private AstExpr parseIfExpr() {
        AstExpr condition = parseExpr();
        AstExpr thenExpr = parseBlockExpr();
        if (!matchKeyword("else")) {
            throw error(peek(), "if expression requires else");
        }
        AstExpr elseExpr = parseBlockExpr();
        return new AstIfExpr(condition, thenExpr, elseExpr);
    }

    private AstExpr parseBlockExpr() {
        expectSymbol("{");
        List<AstStmt> statements = new ArrayList<>();
        AstExpr value = null;
        while (!checkSymbol("}") && !isAtEnd()) {
            if (matchKeyword("let")) {
                statements.add(parseLet());
                continue;
            }
            if (matchKeyword("while")) {
                statements.add(parseWhile());
                continue;
            }
            if (matchKeyword("break")) {
                expectSymbol(";");
                statements.add(new AstBreakStmt());
                continue;
            }
            if (matchKeyword("continue")) {
                expectSymbol(";");
                statements.add(new AstContinueStmt());
                continue;
            }
            if (matchKeyword("return")) {
                statements.add(parseReturn());
                continue;
            }
            AstExpr expr = parseExpr();
            if (matchSymbol(";")) {
                statements.add(new AstExprStmt(expr));
                continue;
            }
            value = expr;
            break;
        }
        expectSymbol("}");
        if (value == null) {
            throw error(previous(), "Block expression must end with a value");
        }
        return new AstBlockExpr(statements, value);
    }

    private AstStmt parseReturn() {
        if (checkSymbol(";")) {
            expectSymbol(";");
            return new AstReturnStmt(null);
        }
        AstExpr expr = parseExpr();
        expectSymbol(";");
        return new AstReturnStmt(expr);
    }

    private AstStruct parseStruct() {
        Token name = expect(Token.TokenKind.IDENT, "Expected struct name");
        expectSymbol("{");
        List<AstField> fields = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            Token fieldName = expect(Token.TokenKind.IDENT, "Expected field name");
            expectSymbol(":");
            String fieldType = parseTypeName();
            fields.add(new AstField(fieldName.lexeme(), fieldType));
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

    private String parseTypeName() {
        if (check(Token.TokenKind.IDENT)) {
            List<String> path = parsePath();
            return String.join("::", path);
        }
        throw error(peek(), "Expected type name");
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
