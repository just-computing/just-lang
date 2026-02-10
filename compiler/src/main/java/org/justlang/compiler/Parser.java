package org.justlang.compiler;

import java.util.ArrayList;
import java.util.List;

public final class Parser implements ParserStrategy {
    private List<Token> tokens;
    private int current;
    private boolean allowStructInit = true;
    private Diagnostics diagnostics;
    private SourceFile sourceFile;
    private final List<AstItem> pendingItems = new ArrayList<>();

    @Override
    public AstModule parse(SourceFile sourceFile, List<Token> tokens, Diagnostics diagnostics) {
        this.sourceFile = sourceFile;
        this.diagnostics = diagnostics;
        this.tokens = tokens;
        this.current = 0;
        this.pendingItems.clear();
        List<AstItem> items = new ArrayList<>();
        while (!isAtEnd() || !pendingItems.isEmpty()) {
            if (!pendingItems.isEmpty()) {
                items.add(pendingItems.remove(0));
                continue;
            }
            skipOuterAttributes();
            if (isAtEnd()) {
                break;
            }
            items.add(parseItem());
        }
        return new AstModule(items);
    }

    private AstItem parseItem() {
        if (matchKeyword("pub")) {
            if (matchKeyword("fn")) {
                return parseFunction(true);
            }
            if (matchKeyword("struct")) {
                return parseStruct();
            }
            if (matchKeyword("enum")) {
                return parseEnum();
            }
            throw error(peek(), "Expected 'fn', 'struct', or 'enum' after 'pub'");
        }
        if (matchKeyword("fn")) {
            return parseFunction(false);
        }
        if (matchKeyword("struct")) {
            return parseStruct();
        }
        if (matchKeyword("enum")) {
            return parseEnum();
        }
        if (matchKeyword("mod")) {
            return parseMod();
        }
        if (matchKeyword("use")) {
            List<AstUse> useItems = parseUseItems();
            if (useItems.isEmpty()) {
                throw error(previous(), "Expected at least one use target");
            }
            for (int i = 1; i < useItems.size(); i++) {
                pendingItems.add(useItems.get(i));
            }
            return useItems.get(0);
        }
        if (matchKeyword("import")) {
            return parseImport();
        }
        throw error(peek(), "Expected item (e.g., 'fn' or 'struct')");
    }

    private AstImport parseImport() {
        Token path = expect(Token.TokenKind.STRING, "Expected string literal import path");
        expectSymbol(";");
        return new AstImport(path.lexeme());
    }

    private AstFunction parseFunction(boolean publicItem) {
        Token name = expect(Token.TokenKind.IDENT, "Expected function name");
        expectSymbol("(");
        List<AstParam> params = new ArrayList<>();
        if (!checkSymbol(")")) {
            while (true) {
                boolean mutable = matchKeyword("mut");
                Token paramName = expect(Token.TokenKind.IDENT, "Expected parameter name");
                expectSymbol(":");
                String paramType = parseTypeName();
                params.add(new AstParam(paramName.lexeme(), paramType, mutable));
                if (!matchSymbol(",")) {
                    break;
                }
                if (checkSymbol(")")) {
                    break;
                }
            }
        }
        expectSymbol(")");
        String returnType = null;
        if (matchSymbol("->")) {
            returnType = parseTypeName();
        }
        List<AstStmt> body = parseBlock();
        return new AstFunction(name.lexeme(), params, returnType, body, publicItem, sourceFile.path());
    }

    private AstImport parseMod() {
        List<String> path = parsePath();
        expectSymbol(";");
        return new AstImport(String.join("/", path) + ".just");
    }

    private List<AstUse> parseUseItems() {
        List<String> basePath = parsePath();
        List<AstUse> items = new ArrayList<>();

        if (checkSymbol(":") && current + 2 < tokens.size()
            && ":".equals(tokens.get(current + 1).lexeme())
            && "{".equals(tokens.get(current + 2).lexeme())) {
            advance();
            advance();
            expectSymbol("{");
            String module = String.join("::", basePath);
            if (module.isBlank()) {
                throw error(previous(), "use group requires a module path");
            }
            do {
                Token symbolToken = expect(Token.TokenKind.IDENT, "Expected symbol name in grouped use");
                String symbol = symbolToken.lexeme();
                String alias = symbol;
                if (matchKeyword("as")) {
                    Token aliasToken = expect(Token.TokenKind.IDENT, "Expected alias after 'as'");
                    alias = aliasToken.lexeme();
                }
                items.add(new AstUse(module, symbol, alias, sourceFile.path()));
                if (!matchSymbol(",")) {
                    break;
                }
                if (checkSymbol("}")) {
                    break;
                }
            } while (true);
            expectSymbol("}");
            expectSymbol(";");
            return items;
        }

        if (basePath.size() < 2) {
            throw error(previous(), "use requires a module and symbol (e.g., use util::helper;)");
        }
        String symbol = basePath.get(basePath.size() - 1);
        String module = String.join("::", basePath.subList(0, basePath.size() - 1));
        String alias = symbol;
        if (matchKeyword("as")) {
            Token aliasToken = expect(Token.TokenKind.IDENT, "Expected alias after 'as'");
            alias = aliasToken.lexeme();
        }
        expectSymbol(";");
        items.add(new AstUse(module, symbol, alias, sourceFile.path()));
        return items;
    }

    private AstStmt parseStatement() {
        if (checkSymbol("'")) {
            String label = parseLabel();
            expectSymbol(":");
            if (matchKeyword("for")) {
                return parseFor(label);
            }
            if (matchKeyword("while")) {
                if (matchKeyword("let")) {
                    return parseWhileLet(label);
                }
                return parseWhile(label);
            }
            if (matchKeyword("loop")) {
                return parseLoopStmt(label);
            }
            throw error(peek(), "Labels can only be applied to loops");
        }
        if (matchKeyword("if")) {
            if (matchKeyword("let")) {
                return parseIfLet();
            }
            return parseIf();
        }
        if (matchKeyword("for")) {
            return parseFor(null);
        }
        if (matchKeyword("loop")) {
            return parseLoopStmt(null);
        }
        if (matchKeyword("while")) {
            if (matchKeyword("let")) {
                return parseWhileLet(null);
            }
            return parseWhile(null);
        }
        if (matchKeyword("break")) {
            return parseBreak();
        }
        if (matchKeyword("continue")) {
            return parseContinue();
        }
        if (matchKeyword("return")) {
            return parseReturn();
        }
        if (matchKeyword("let")) {
            return parseLet();
        }
        if (checkAssignment()) {
            return parseAssign();
        }
        AstExpr expr = parseExpr();
        expectSymbol(";");
        return new AstExprStmt(expr);
    }

    private AstStmt parseLet() {
        boolean mutable = matchKeyword("mut");
        Token name = expect(Token.TokenKind.IDENT, "Expected identifier after 'let'");
        String type = null;
        if (matchSymbol(":")) {
            type = parseTypeName();
        }
        AstExpr initializer = null;
        if (!matchSymbol("=")) {
            throw error(peek(), "Expected '=' after let binding");
        }
        initializer = parseExpr();
        expectSymbol(";");
        return new AstLetStmt(name.lexeme(), mutable, type, initializer);
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
        if (matchSymbol("&")) {
            boolean mutable = matchKeyword("mut");
            AstExpr right = parseUnary();
            return new AstUnaryExpr(mutable ? "&mut" : "&", right);
        }
        if (matchSymbol("*")) {
            AstExpr right = parseUnary();
            return new AstUnaryExpr("*", right);
        }
        AstExpr expr = parsePrimary();
        while (matchSymbol(".")) {
            Token field = expect(Token.TokenKind.IDENT, "Expected field name after '.'");
            expr = new AstFieldAccessExpr(expr, field.lexeme());
        }
        if (matchSymbol("?")) {
            throw error(previous(), "Try operator '?' is not supported yet");
        }
        return expr;
    }

    private AstExpr parsePrimary() {
        if (matchKeyword("match")) {
            return parseMatchExpr();
        }
        if (matchKeyword("loop")) {
            return parseLoopExpr();
        }
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
            if (matchSymbol("!")) {
                expectSymbol("(");
                List<AstExpr> args = new ArrayList<>();
                if (!checkSymbol(")")) {
                    while (true) {
                        args.add(parseExpr());
                        if (!matchSymbol(",")) {
                            break;
                        }
                        if (checkSymbol(")")) {
                            break;
                        }
                    }
                }
                expectSymbol(")");
                return new AstCallExpr(path, args);
            }
            if (allowStructInit && path.size() == 1 && matchSymbol("{")) {
                List<AstFieldInit> fields = new ArrayList<>();
                if (!checkSymbol("}")) {
                    while (true) {
                        Token fieldName = expect(Token.TokenKind.IDENT, "Expected field name");
                        expectSymbol(":");
                        AstExpr value = parseExpr();
                        fields.add(new AstFieldInit(fieldName.lexeme(), value));
                        if (!matchSymbol(",")) {
                            break;
                        }
                        if (checkSymbol("}")) {
                            break;
                        }
                    }
                }
                expectSymbol("}");
                return new AstStructInitExpr(path.get(0), fields);
            }
            if (matchSymbol("(")) {
                List<AstExpr> args = new ArrayList<>();
                if (!checkSymbol(")")) {
                    while (true) {
                        args.add(parseExpr());
                        if (!matchSymbol(",")) {
                            break;
                        }
                        if (checkSymbol(")")) {
                            break;
                        }
                    }
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

    private void skipOuterAttributes() {
        while (matchSymbol("#")) {
            expectSymbol("[");
            int depth = 1;
            while (depth > 0) {
                if (isAtEnd()) {
                    throw error(previous(), "Unterminated attribute");
                }
                if (matchSymbol("[")) {
                    depth++;
                    continue;
                }
                if (matchSymbol("]")) {
                    depth--;
                    continue;
                }
                advance();
            }
        }
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
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr condition = parseExpr();
        allowStructInit = previous;
        List<AstStmt> thenBranch = parseBlock();
        List<AstStmt> elseBranch = null;
        if (matchKeyword("else")) {
            if (matchKeyword("if")) {
                List<AstStmt> nested = new ArrayList<>();
                nested.add(parseIf());
                elseBranch = nested;
            } else {
                elseBranch = parseBlock();
            }
        }
        return new AstIfStmt(condition, thenBranch, elseBranch);
    }

    private AstStmt parseWhile(String label) {
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr condition = parseExpr();
        allowStructInit = previous;
        List<AstStmt> body = parseBlock();
        return new AstWhileStmt(label, condition, body);
    }

    private AstStmt parseWhileLet(String label) {
        AstMatchPattern pattern = parseMatchPattern();
        expectSymbol("=");
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr target = parseExpr();
        allowStructInit = previous;
        List<AstStmt> body = parseBlock();
        return new AstWhileLetStmt(label, pattern, target, body);
    }

    private AstStmt parseFor(String label) {
        Token name = expect(Token.TokenKind.IDENT, "Expected loop variable name");
        if (!matchKeyword("in")) {
            throw error(peek(), "Expected 'in' after loop variable");
        }
        AstExpr start = parseExpr();
        boolean inclusive;
        if (matchSymbol("..=")) {
            inclusive = true;
        } else {
            expectSymbol("..");
            inclusive = false;
        }
        AstExpr end = parseExpr();
        List<AstStmt> body = parseBlock();
        return new AstForStmt(label, name.lexeme(), start, end, inclusive, body);
    }

    private AstExpr parseIfExpr() {
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr condition = parseExpr();
        allowStructInit = previous;
        AstExpr thenExpr = parseBlockExpr();
        if (!matchKeyword("else")) {
            throw error(peek(), "if expression requires else");
        }
        AstExpr elseExpr;
        if (matchKeyword("if")) {
            elseExpr = parseIfExpr();
        } else {
            elseExpr = parseBlockExpr();
        }
        return new AstIfExpr(condition, thenExpr, elseExpr);
    }

    private AstStmt parseIfLet() {
        AstMatchPattern pattern = parseMatchPattern();
        expectSymbol("=");
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr target = parseExpr();
        allowStructInit = previous;
        List<AstStmt> thenBranch = parseBlock();
        List<AstStmt> elseBranch = null;
        if (matchKeyword("else")) {
            if (matchKeyword("if")) {
                if (matchKeyword("let")) {
                    elseBranch = new ArrayList<>();
                    elseBranch.add(parseIfLet());
                } else {
                    elseBranch = new ArrayList<>();
                    elseBranch.add(parseIf());
                }
            } else {
                elseBranch = parseBlock();
            }
        }
        return new AstIfLetStmt(pattern, target, thenBranch, elseBranch);
    }

    private AstExpr parseBlockExpr() {
        expectSymbol("{");
        List<AstStmt> statements = new ArrayList<>();
        AstExpr value = null;
        while (!checkSymbol("}") && !isAtEnd()) {
            if (checkSymbol("'")) {
                String label = parseLabel();
                expectSymbol(":");
                if (matchKeyword("for")) {
                    statements.add(parseFor(label));
                    continue;
                }
                if (matchKeyword("while")) {
                    statements.add(parseWhile(label));
                    continue;
                }
                if (matchKeyword("loop")) {
                    statements.add(parseLoopStmt(label));
                    continue;
                }
                throw error(peek(), "Labels can only be applied to loops");
            }
            if (matchKeyword("let")) {
                statements.add(parseLet());
                continue;
            }
            if (matchKeyword("for")) {
                statements.add(parseFor(null));
                continue;
            }
            if (matchKeyword("loop")) {
                statements.add(parseLoopStmt(null));
                continue;
            }
            if (matchKeyword("while")) {
                if (matchKeyword("let")) {
                    statements.add(parseWhileLet(null));
                } else {
                    statements.add(parseWhile(null));
                }
                continue;
            }
            if (matchKeyword("break")) {
                statements.add(parseBreak());
                continue;
            }
            if (matchKeyword("continue")) {
                statements.add(parseContinue());
                continue;
            }
            if (matchKeyword("return")) {
                statements.add(parseReturn());
                continue;
            }
            if (checkAssignment()) {
                statements.add(parseAssign());
                continue;
            }
            if (matchKeyword("if")) {
                AstExpr ifExpr;
                if (matchKeyword("let")) {
                    AstStmt ifLet = parseIfLet();
                    statements.add(ifLet);
                    continue;
                }
                ifExpr = parseIfExpr();
                if (matchSymbol(";")) {
                    statements.add(new AstExprStmt(ifExpr));
                    continue;
                }
                value = ifExpr;
                break;
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

    private AstStmt parseAssign() {
        Token name = expect(Token.TokenKind.IDENT, "Expected identifier");
        String operator;
        if (matchSymbol("=")) {
            operator = "=";
        } else if (matchSymbol("+=")) {
            operator = "+=";
        } else if (matchSymbol("-=")) {
            operator = "-=";
        } else if (matchSymbol("*=")) {
            operator = "*=";
        } else if (matchSymbol("/=")) {
            operator = "/=";
        } else {
            throw error(peek(), "Expected assignment operator");
        }
        AstExpr value = parseExpr();
        expectSymbol(";");
        return new AstAssignStmt(name.lexeme(), operator, value);
    }

    private AstStmt parseBreak() {
        String label = null;
        if (matchSymbol("'")) {
            Token labelToken = expect(Token.TokenKind.IDENT, "Expected label after '''");
            label = labelToken.lexeme();
        }
        AstExpr expr = null;
        if (!checkSymbol(";")) {
            expr = parseExpr();
        }
        expectSymbol(";");
        return new AstBreakStmt(label, expr);
    }

    private AstStmt parseContinue() {
        String label = null;
        if (matchSymbol("'")) {
            Token labelToken = expect(Token.TokenKind.IDENT, "Expected label after '''");
            label = labelToken.lexeme();
        }
        expectSymbol(";");
        return new AstContinueStmt(label);
    }

    private AstExpr parseLoopExpr() {
        List<AstStmt> body = parseBlock();
        return new AstLoopExpr(body);
    }

    private AstStmt parseLoopStmt(String label) {
        List<AstStmt> body = parseBlock();
        return new AstLoopStmt(label, body);
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
            matchKeyword("pub");
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

    private AstEnum parseEnum() {
        Token name = expect(Token.TokenKind.IDENT, "Expected enum name");
        expectSymbol("{");
        List<AstEnumVariant> variants = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            Token variantName = expect(Token.TokenKind.IDENT, "Expected variant name");
            String payloadType = null;
            if (matchSymbol("(")) {
                payloadType = parseTypeName();
                expectSymbol(")");
            }
            variants.add(new AstEnumVariant(variantName.lexeme(), payloadType));
            if (!matchSymbol(",")) {
                break;
            }
        }
        expectSymbol("}");
        return new AstEnum(name.lexeme(), variants);
    }

    private List<String> parsePath() {
        List<String> segments = new ArrayList<>();
        Token first = expect(Token.TokenKind.IDENT, "Expected identifier");
        segments.add(first.lexeme());
        while (matchDoubleColonPath()) {
            Token segment = expect(Token.TokenKind.IDENT, "Expected identifier after '::'");
            segments.add(segment.lexeme());
        }
        return segments;
    }

    private String parseTypeName() {
        if (matchSymbol("&")) {
            boolean mutable = matchKeyword("mut");
            String inner = parseTypeName();
            return mutable ? "&mut " + inner : "&" + inner;
        }
        if (check(Token.TokenKind.IDENT)) {
            List<String> path = parsePath();
            String base = String.join("::", path);
            if (matchSymbol("<")) {
                List<String> typeArgs = new ArrayList<>();
                while (true) {
                    typeArgs.add(parseTypeName());
                    if (!matchSymbol(",")) {
                        break;
                    }
                    if (checkSymbol(">")) {
                        break;
                    }
                }
                expectSymbol(">");
                return base + "<" + String.join(", ", typeArgs) + ">";
            }
            return base;
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

    private boolean matchDoubleColonPath() {
        if (!checkSymbol(":")) {
            return false;
        }
        if (current + 2 >= tokens.size()) {
            return false;
        }
        if (!tokens.get(current + 1).lexeme().equals(":")) {
            return false;
        }
        if (tokens.get(current + 2).kind() != Token.TokenKind.IDENT) {
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

    private boolean checkAssignment() {
        if (!check(Token.TokenKind.IDENT)) {
            return false;
        }
        if (current + 1 >= tokens.size()) {
            return false;
        }
        Token next = tokens.get(current + 1);
        return next.kind() == Token.TokenKind.SYMBOL
            && ("=".equals(next.lexeme())
                || "+=".equals(next.lexeme())
                || "-=".equals(next.lexeme())
                || "*=".equals(next.lexeme())
                || "/=".equals(next.lexeme()));
    }

    private String parseLabel() {
        expectSymbol("'");
        Token label = expect(Token.TokenKind.IDENT, "Expected label name");
        return label.lexeme();
    }

    private AstExpr parseMatchExpr() {
        boolean previous = allowStructInit;
        allowStructInit = false;
        AstExpr target = parseExpr();
        allowStructInit = previous;
        expectSymbol("{");
        List<AstMatchArm> arms = new ArrayList<>();
        while (!checkSymbol("}") && !isAtEnd()) {
            AstMatchPattern pattern = parseMatchPattern();
            AstExpr guard = null;
            if (matchKeyword("if")) {
                guard = parseExpr();
            }
            expectSymbol("=>");
            AstExpr value;
            if (checkSymbol("{")) {
                value = parseBlockExpr();
            } else {
                value = parseExpr();
            }
            arms.add(new AstMatchArm(pattern, guard, value));
            if (matchSymbol(",")) {
                continue;
            }
            if (!checkSymbol("}")) {
                throw error(peek(), "Expected ',' or '}' after match arm");
            }
        }
        expectSymbol("}");
        return new AstMatchExpr(target, arms);
    }

    private AstMatchPattern parseMatchPattern() {
        if (check(Token.TokenKind.IDENT)) {
            List<String> path = parsePath();
            if (path.size() == 1 && "_".equals(path.get(0))) {
                return AstMatchPattern.wildcard();
            }
            if (path.size() == 2) {
                String enumName = path.get(0);
                String variantName = path.get(1);
                String binding = null;
                if (matchSymbol("(")) {
                    Token bind = expect(Token.TokenKind.IDENT, "Expected binding name");
                    binding = bind.lexeme();
                    expectSymbol(")");
                }
                return AstMatchPattern.enumVariant(enumName, variantName, binding);
            }
            throw error(peek(), "Unsupported match pattern");
        }
        if (matchKeyword("true")) {
            return AstMatchPattern.boolLiteral("true");
        }
        if (matchKeyword("false")) {
            return AstMatchPattern.boolLiteral("false");
        }
        if (match(Token.TokenKind.NUMBER)) {
            String start = previous().lexeme();
            if (matchSymbol("..=")) {
                Token end = expect(Token.TokenKind.NUMBER, "Expected range end");
                return AstMatchPattern.range(start, end.lexeme(), true);
            }
            if (matchSymbol("..")) {
                Token end = expect(Token.TokenKind.NUMBER, "Expected range end");
                return AstMatchPattern.range(start, end.lexeme(), false);
            }
            return AstMatchPattern.intLiteral(start);
        }
        if (match(Token.TokenKind.STRING)) {
            return AstMatchPattern.stringLiteral(previous().lexeme());
        }
        throw error(peek(), "Unsupported match pattern");
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
        String full = message + " at " + token.line() + ":" + token.column();
        if (diagnostics != null && sourceFile != null) {
            diagnostics.report(new Diagnostic(full, sourceFile.path()));
        }
        return new ParseException(full);
    }
}
