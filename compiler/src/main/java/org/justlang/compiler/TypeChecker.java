package org.justlang.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class TypeChecker {
    private TypeId currentReturnType = TypeId.VOID;
    private final Deque<LoopContext> loopStack = new ArrayDeque<>();

    public TypedModule typeCheck(HirModule module) {
        throw new UnsupportedOperationException("Type checker not implemented yet");
    }

    public TypeResult typeCheck(AstModule module) {
        TypeEnvironment diags = new TypeEnvironment();
        StructRegistry structs = new StructRegistry();
        FunctionRegistry functions = new FunctionRegistry();
        boolean success = true;

        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structs.register(struct);
            }
        }

        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn) {
                if (functions.contains(fn.name())) {
                    diags.addError("Duplicate function: " + fn.name());
                    success = false;
                    continue;
                }

                if ("main".equals(fn.name()) && !fn.params().isEmpty()) {
                    diags.addError("main does not accept parameters");
                    success = false;
                }

                List<TypeId> paramTypes = new ArrayList<>();
                for (AstParam param : fn.params()) {
                    TypeId paramType = resolveTypeName(param.type(), structs);
                    if (paramType == TypeId.UNKNOWN) {
                        diags.addError("Unknown parameter type: " + param.type());
                        success = false;
                        continue;
                    }
                    if (paramType == TypeId.VOID) {
                        diags.addError("Parameter type cannot be void");
                        success = false;
                        continue;
                    }
                    paramTypes.add(paramType);
                }

                TypeId returnType = resolveReturnType(fn.returnType(), structs, diags);
                if (returnType == TypeId.UNKNOWN) {
                    success = false;
                }
                if ("main".equals(fn.name()) && returnType != TypeId.VOID) {
                    diags.addError("main must return void");
                    success = false;
                }

                functions.register(fn.name(), new FunctionRegistry.FunctionSig(fn.name(), returnType, paramTypes));
            }
        }

        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn) {
                if (!checkFunction(fn, structs, functions, diags)) {
                    success = false;
                }
                continue;
            }
            if (!(item instanceof AstStruct)) {
                diags.addError("Unsupported item: " + item.getClass().getSimpleName());
                success = false;
            }
        }

        return new TypeResult(success, diags);
    }

    private boolean checkFunction(AstFunction fn, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        boolean success = true;
        TypeId expectedReturn = resolveReturnType(fn.returnType(), structs, diags);
        if (expectedReturn == TypeId.UNKNOWN) {
            return false;
        }

        FunctionRegistry.FunctionSig sig = functions.find(fn.name());
        if (sig == null) {
            diags.addError("Unknown function signature for " + fn.name());
            return false;
        }

        TypeEnvironment locals = new TypeEnvironment();
        List<TypeId> paramTypes = sig.paramTypes();
        for (int i = 0; i < fn.params().size(); i++) {
            TypeId paramType = paramTypes.get(i);
            locals.define(fn.params().get(i).name(), paramType, false);
        }

        TypeId previousReturn = currentReturnType;
        currentReturnType = expectedReturn;
        if (!checkBlock(fn.body(), locals, structs, functions, expectedReturn, diags)) {
            success = false;
        }
        currentReturnType = previousReturn;

        if (expectedReturn != TypeId.VOID && !endsWithReturnValue(fn.body())) {
            diags.addError("Non-void functions must return on all paths");
            success = false;
        }

        return success;
    }

    private boolean checkBlock(
        List<AstStmt> statements,
        TypeEnvironment locals,
        StructRegistry structs,
        FunctionRegistry functions,
        TypeId expectedReturn,
        TypeEnvironment diags
    ) {
        boolean success = true;
        for (AstStmt stmt : statements) {
            if (stmt instanceof AstLetStmt letStmt) {
                if (letStmt.initializer() == null) {
                    diags.addError("let without initializer is not supported yet");
                    success = false;
                    continue;
                }
                TypeId exprType = inferExpr(letStmt.initializer(), locals, structs, functions, diags);
                if (exprType == TypeId.UNKNOWN || exprType == TypeId.VOID) {
                    if (exprType == TypeId.VOID) {
                        diags.addError("let initializer cannot be void");
                    }
                    success = false;
                    continue;
                }
                locals.define(letStmt.name(), exprType, letStmt.mutable());
                continue;
            }
            if (stmt instanceof AstAssignStmt assignStmt) {
                TypeEnvironment.Binding binding = locals.lookup(assignStmt.name());
                if (binding == null) {
                    diags.addError("Unknown identifier: " + assignStmt.name());
                    success = false;
                    continue;
                }
                if (!binding.mutable()) {
                    diags.addError("Cannot assign to immutable variable: " + assignStmt.name());
                    success = false;
                    continue;
                }
                TypeId valueType = inferExpr(assignStmt.value(), locals, structs, functions, diags);
                if (valueType == TypeId.UNKNOWN) {
                    success = false;
                    continue;
                }
                if (valueType == TypeId.VOID) {
                    diags.addError("assignment value cannot be void");
                    success = false;
                    continue;
                }
                String op = assignStmt.operator();
                if ("=".equals(op)) {
                    if (!binding.type().equals(valueType)) {
                        diags.addError("Type mismatch in assignment to " + assignStmt.name());
                        success = false;
                    }
                } else {
                    if (binding.type() != TypeId.INT || valueType != TypeId.INT) {
                        diags.addError("Compound assignment requires int operands");
                        success = false;
                    }
                }
                continue;
            }
            if (stmt instanceof AstExprStmt exprStmt) {
                TypeId exprType = inferExpr(exprStmt.expr(), locals, structs, functions, diags);
                if (exprType == TypeId.UNKNOWN) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstIfStmt ifStmt) {
                TypeId condType = inferExpr(ifStmt.condition(), locals, structs, functions, diags);
                if (condType != TypeId.BOOL) {
                    diags.addError("if condition must be bool");
                    success = false;
                }
                if (!checkBlock(ifStmt.thenBranch(), locals.fork(), structs, functions, expectedReturn, diags)) {
                    success = false;
                }
                if (ifStmt.elseBranch() != null) {
                    if (!checkBlock(ifStmt.elseBranch(), locals.fork(), structs, functions, expectedReturn, diags)) {
                        success = false;
                    }
                }
                continue;
            }
            if (stmt instanceof AstWhileStmt whileStmt) {
                TypeId condType = inferExpr(whileStmt.condition(), locals, structs, functions, diags);
                if (condType != TypeId.BOOL) {
                    diags.addError("while condition must be bool");
                    success = false;
                }
                loopStack.push(new LoopContext(whileStmt.label(), false));
                if (!checkBlock(whileStmt.body(), locals.fork(), structs, functions, expectedReturn, diags)) {
                    success = false;
                }
                loopStack.pop();
                continue;
            }
            if (stmt instanceof AstForStmt forStmt) {
                TypeId startType = inferExpr(forStmt.start(), locals, structs, functions, diags);
                TypeId endType = inferExpr(forStmt.end(), locals, structs, functions, diags);
                if (startType != TypeId.INT || endType != TypeId.INT) {
                    diags.addError("for loop bounds must be int");
                    success = false;
                }
                TypeEnvironment loopLocals = locals.fork();
                loopLocals.define(forStmt.name(), TypeId.INT, false);
                loopStack.push(new LoopContext(forStmt.label(), false));
                if (!checkBlock(forStmt.body(), loopLocals, structs, functions, expectedReturn, diags)) {
                    success = false;
                }
                loopStack.pop();
                continue;
            }
            if (stmt instanceof AstLoopStmt loopStmt) {
                loopStack.push(new LoopContext(loopStmt.label(), false));
                if (!checkBlock(loopStmt.body(), locals.fork(), structs, functions, expectedReturn, diags)) {
                    success = false;
                }
                loopStack.pop();
                continue;
            }
            if (stmt instanceof AstBreakStmt breakStmt) {
                if (!checkBreak(breakStmt, locals, structs, functions, diags)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstContinueStmt continueStmt) {
                if (!checkContinue(continueStmt, diags)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstReturnStmt returnStmt) {
                if (expectedReturn == TypeId.VOID) {
                    if (returnStmt.expr() != null) {
                        diags.addError("return with value in void function");
                        success = false;
                    }
                    continue;
                }
                if (returnStmt.expr() == null) {
                    diags.addError("return without value in non-void function");
                    success = false;
                    continue;
                }
                TypeId exprType = inferExpr(returnStmt.expr(), locals, structs, functions, diags);
                if (!expectedReturn.equals(exprType)) {
                    diags.addError("return type mismatch: expected " + expectedReturn + " got " + exprType);
                    success = false;
                }
                continue;
            }
            diags.addError("Unsupported statement: " + stmt.getClass().getSimpleName());
            success = false;
        }
        return success;
    }

    private boolean endsWithReturnValue(List<AstStmt> statements) {
        return alwaysReturns(statements);
    }

    private boolean alwaysReturns(List<AstStmt> statements) {
        for (AstStmt stmt : statements) {
            if (stmt instanceof AstReturnStmt returnStmt) {
                return returnStmt.expr() != null;
            }
            if (stmt instanceof AstIfStmt ifStmt) {
                if (ifStmt.elseBranch() != null
                    && alwaysReturns(ifStmt.thenBranch())
                    && alwaysReturns(ifStmt.elseBranch())) {
                    return true;
                }
            }
        }
        return false;
    }

    private TypeId inferExpr(AstExpr expr, TypeEnvironment locals, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        if (expr instanceof AstStringExpr) {
            return TypeId.STRING;
        }
        if (expr instanceof AstNumberExpr) {
            return TypeId.INT;
        }
        if (expr instanceof AstBoolExpr) {
            return TypeId.BOOL;
        }
        if (expr instanceof AstIdentExpr identExpr) {
            TypeEnvironment.Binding binding = locals.lookup(identExpr.name());
            if (binding == null) {
                diags.addError("Unknown identifier: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            return binding.type();
        }
        if (expr instanceof AstStructInitExpr initExpr) {
            StructRegistry.StructDef def = structs.find(initExpr.name());
            if (def == null) {
                diags.addError("Unknown struct: " + initExpr.name());
                return TypeId.UNKNOWN;
            }
            for (AstField field : def.fields()) {
                boolean found = initExpr.fields().stream().anyMatch(f -> f.name().equals(field.name()));
                if (!found) {
                    diags.addError("Missing field '" + field.name() + "' for struct " + def.name());
                    return TypeId.UNKNOWN;
                }
            }
            for (AstFieldInit field : initExpr.fields()) {
                AstField target = def.field(field.name());
                if (target == null) {
                    diags.addError("Unknown field '" + field.name() + "' on struct " + def.name());
                    return TypeId.UNKNOWN;
                }
                TypeId valueType = inferExpr(field.value(), locals, structs, functions, diags);
                TypeId fieldType = resolveTypeName(target.type(), structs);
                if (fieldType == TypeId.UNKNOWN) {
                    diags.addError("Unsupported field type: " + target.type());
                    return TypeId.UNKNOWN;
                }
                if (!fieldType.equals(valueType)) {
                    diags.addError("Type mismatch for field '" + field.name() + "': expected " + fieldType + " got " + valueType);
                    return TypeId.UNKNOWN;
                }
            }
            return TypeId.struct(def.name());
        }
        if (expr instanceof AstFieldAccessExpr accessExpr) {
            TypeId targetType = inferExpr(accessExpr.target(), locals, structs, functions, diags);
            if (!targetType.isStruct()) {
                diags.addError("Field access on non-struct type: " + targetType);
                return TypeId.UNKNOWN;
            }
            StructRegistry.StructDef def = structs.find(targetType.structName());
            if (def == null) {
                diags.addError("Unknown struct type: " + targetType.structName());
                return TypeId.UNKNOWN;
            }
            AstField field = def.field(accessExpr.field());
            if (field == null) {
                diags.addError("Unknown field '" + accessExpr.field() + "' on struct " + def.name());
                return TypeId.UNKNOWN;
            }
            return resolveTypeName(field.type(), structs);
        }
        if (expr instanceof AstBinaryExpr binaryExpr) {
            return inferBinary(binaryExpr, locals, structs, functions, diags);
        }
        if (expr instanceof AstUnaryExpr unaryExpr) {
            return inferUnary(unaryExpr, locals, structs, functions, diags);
        }
        if (expr instanceof AstCallExpr callExpr) {
            return inferCall(callExpr, locals, structs, functions, diags);
        }
        if (expr instanceof AstIfExpr ifExpr) {
            TypeId condType = inferExpr(ifExpr.condition(), locals, structs, functions, diags);
            if (condType != TypeId.BOOL) {
                diags.addError("if expression condition must be bool");
                return TypeId.UNKNOWN;
            }
            TypeId thenType = inferExpr(ifExpr.thenExpr(), locals, structs, functions, diags);
            TypeId elseType = inferExpr(ifExpr.elseExpr(), locals, structs, functions, diags);
            if (thenType == TypeId.UNKNOWN || elseType == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            if (!thenType.equals(elseType)) {
                diags.addError("if expression branches must match: " + thenType + " vs " + elseType);
                return TypeId.UNKNOWN;
            }
            if (thenType == TypeId.VOID) {
                diags.addError("if expression cannot be void");
                return TypeId.UNKNOWN;
            }
            return thenType;
        }
        if (expr instanceof AstBlockExpr blockExpr) {
            TypeEnvironment blockLocals = locals.fork();
            if (!checkBlock(blockExpr.statements(), blockLocals, structs, functions, currentReturnType, diags)) {
                return TypeId.UNKNOWN;
            }
            TypeId valueType = inferExpr(blockExpr.value(), blockLocals, structs, functions, diags);
            if (valueType == TypeId.VOID) {
                diags.addError("block expression cannot be void");
                return TypeId.UNKNOWN;
            }
            return valueType;
        }
        if (expr instanceof AstLoopExpr loopExpr) {
            LoopContext context = new LoopContext(null, true);
            loopStack.push(context);
            if (!checkBlock(loopExpr.body(), locals.fork(), structs, functions, currentReturnType, diags)) {
                loopStack.pop();
                return TypeId.UNKNOWN;
            }
            loopStack.pop();
            if (context.breakType == null) {
                diags.addError("loop expression requires break with value");
                return TypeId.UNKNOWN;
            }
            return context.breakType;
        }
        if (expr instanceof AstMatchExpr matchExpr) {
            if (matchExpr.arms().isEmpty()) {
                diags.addError("match requires at least one arm");
                return TypeId.UNKNOWN;
            }
            TypeId targetType = inferExpr(matchExpr.target(), locals, structs, functions, diags);
            if (targetType == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            if (targetType != TypeId.INT && targetType != TypeId.BOOL && targetType != TypeId.STRING) {
                diags.addError("match target must be int, bool, or String");
                return TypeId.UNKNOWN;
            }
            boolean hasWildcard = false;
            TypeId armType = null;
            List<AstMatchArm> arms = matchExpr.arms();
            for (int i = 0; i < arms.size(); i++) {
                AstMatchArm arm = arms.get(i);
                AstMatchPattern pattern = arm.pattern();
                if (pattern.kind() == AstMatchPattern.Kind.WILDCARD) {
                    hasWildcard = true;
                    if (i != arms.size() - 1) {
                        diags.addError("wildcard '_' must be the last match arm");
                        return TypeId.UNKNOWN;
                    }
                } else {
                    if (!patternMatchesType(pattern, targetType)) {
                        diags.addError("match pattern does not match target type");
                        return TypeId.UNKNOWN;
                    }
                    if (pattern.kind() == AstMatchPattern.Kind.RANGE) {
                        int start = Integer.parseInt(pattern.rangeStart());
                        int end = Integer.parseInt(pattern.rangeEnd());
                        if (start > end) {
                            diags.addError("match range start must be <= end");
                            return TypeId.UNKNOWN;
                        }
                    }
                }
                TypeId valueType = inferExpr(arm.expr(), locals, structs, functions, diags);
                if (valueType == TypeId.UNKNOWN) {
                    return TypeId.UNKNOWN;
                }
                if (valueType == TypeId.VOID) {
                    diags.addError("match arm cannot be void");
                    return TypeId.UNKNOWN;
                }
                if (armType == null) {
                    armType = valueType;
                } else if (!armType.equals(valueType)) {
                    diags.addError("match arms must return the same type");
                    return TypeId.UNKNOWN;
                }
            }
            if (!hasWildcard) {
                diags.addWarning("match expression is non-exhaustive (missing '_')");
            }
            return armType == null ? TypeId.UNKNOWN : armType;
        }
        if (expr instanceof AstPathExpr pathExpr) {
            diags.addError("Unsupported path expression: " + String.join("::", pathExpr.segments()));
            return TypeId.UNKNOWN;
        }
        diags.addError("Unsupported expression: " + expr.getClass().getSimpleName());
        return TypeId.UNKNOWN;
    }

    private TypeId inferCall(AstCallExpr callExpr, TypeEnvironment locals, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        if (isPrintCall(callExpr)) {
            if (callExpr.args().size() != 1) {
                diags.addError("print expects exactly one argument");
                return TypeId.UNKNOWN;
            }
            TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, functions, diags);
            if (!argType.isPrintable()) {
                diags.addError("print does not support type: " + argType);
                return TypeId.UNKNOWN;
            }
            return TypeId.VOID;
        }

        if (callExpr.callee().size() != 1) {
            diags.addError("Only direct function calls are supported");
            return TypeId.UNKNOWN;
        }

        String name = callExpr.callee().get(0);
        FunctionRegistry.FunctionSig sig = functions.find(name);
        if (sig == null) {
            diags.addError("Unknown function: " + name);
            return TypeId.UNKNOWN;
        }

        if (sig.paramCount() != callExpr.args().size()) {
            diags.addError("Function '" + name + "' expects " + sig.paramCount() + " arguments");
            return TypeId.UNKNOWN;
        }

        List<TypeId> paramTypes = sig.paramTypes();
        for (int i = 0; i < callExpr.args().size(); i++) {
            TypeId argType = inferExpr(callExpr.args().get(i), locals, structs, functions, diags);
            if (!paramTypes.get(i).equals(argType)) {
                diags.addError("Argument " + (i + 1) + " of '" + name + "' expected " + paramTypes.get(i) + " got " + argType);
                return TypeId.UNKNOWN;
            }
        }

        return sig.returnType();
    }

    private TypeId inferBinary(AstBinaryExpr expr, TypeEnvironment locals, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        TypeId left = inferExpr(expr.left(), locals, structs, functions, diags);
        TypeId right = inferExpr(expr.right(), locals, structs, functions, diags);
        String op = expr.operator();

        if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
            if (left == TypeId.INT && right == TypeId.INT) {
                return TypeId.INT;
            }
            diags.addError("Arithmetic operator requires int operands");
            return TypeId.UNKNOWN;
        }

        if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
            if (left == TypeId.INT && right == TypeId.INT) {
                return TypeId.BOOL;
            }
            diags.addError("Comparison operator requires int operands");
            return TypeId.UNKNOWN;
        }

        if ("==".equals(op) || "!=".equals(op)) {
            if (left.equals(right) && (left == TypeId.INT || left == TypeId.BOOL || left == TypeId.STRING || left.isStruct())) {
                return TypeId.BOOL;
            }
            diags.addError("Equality requires matching operand types");
            return TypeId.UNKNOWN;
        }

        if ("&&".equals(op) || "||".equals(op)) {
            if (left == TypeId.BOOL && right == TypeId.BOOL) {
                return TypeId.BOOL;
            }
            diags.addError("Logical operator requires bool operands");
            return TypeId.UNKNOWN;
        }

        diags.addError("Unsupported operator: " + op);
        return TypeId.UNKNOWN;
    }

    private TypeId inferUnary(AstUnaryExpr expr, TypeEnvironment locals, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        TypeId right = inferExpr(expr.expr(), locals, structs, functions, diags);
        if ("!".equals(expr.operator())) {
            if (right == TypeId.BOOL) {
                return TypeId.BOOL;
            }
            diags.addError("Unary ! requires bool operand");
            return TypeId.UNKNOWN;
        }
        if ("-".equals(expr.operator())) {
            if (right == TypeId.INT) {
                return TypeId.INT;
            }
            diags.addError("Unary - requires int operand");
            return TypeId.UNKNOWN;
        }
        diags.addError("Unsupported unary operator: " + expr.operator());
        return TypeId.UNKNOWN;
    }

    private TypeId resolveReturnType(String name, StructRegistry structs, TypeEnvironment diags) {
        if (name == null) {
            return TypeId.VOID;
        }
        TypeId type = resolveTypeName(name, structs);
        if (type == TypeId.UNKNOWN) {
            diags.addError("Unknown type: " + name);
        }
        return type;
    }

    private TypeId resolveTypeName(String name, StructRegistry structs) {
        TypeId base = TypeId.fromTypeName(name);
        if (base != TypeId.UNKNOWN) {
            return base;
        }
        if (structs.find(name) != null) {
            return TypeId.struct(name);
        }
        return TypeId.UNKNOWN;
    }

    private boolean isPrintCall(AstCallExpr callExpr) {
        if (callExpr.callee().size() == 1) {
            return "print".equals(callExpr.callee().get(0));
        }
        return callExpr.callee().size() == 2
            && "std".equals(callExpr.callee().get(0))
            && "print".equals(callExpr.callee().get(1));
    }

    private boolean checkBreak(AstBreakStmt breakStmt, TypeEnvironment locals, StructRegistry structs, FunctionRegistry functions, TypeEnvironment diags) {
        LoopContext context = resolveLoopContext(breakStmt.label(), diags, "break");
        if (context == null) {
            return false;
        }
        if (breakStmt.expr() != null) {
            if (!context.allowsValue) {
                diags.addError("break with value is only allowed in loop expressions");
                return false;
            }
            TypeId valueType = inferExpr(breakStmt.expr(), locals, structs, functions, diags);
            if (valueType == TypeId.UNKNOWN || valueType == TypeId.VOID) {
                diags.addError("break value must be a non-void expression");
                return false;
            }
            if (context.breakType == null) {
                context.breakType = valueType;
            } else if (!context.breakType.equals(valueType)) {
                diags.addError("break values in loop expression must have the same type");
                return false;
            }
            return true;
        }

        if (context.allowsValue) {
            diags.addError("break value required for loop expression");
            return false;
        }
        return true;
    }

    private boolean checkContinue(AstContinueStmt continueStmt, TypeEnvironment diags) {
        LoopContext context = resolveLoopContext(continueStmt.label(), diags, "continue");
        return context != null;
    }

    private LoopContext resolveLoopContext(String label, TypeEnvironment diags, String keyword) {
        if (loopStack.isEmpty()) {
            diags.addError(keyword + " is only valid inside loops");
            return null;
        }
        if (label == null) {
            return loopStack.peek();
        }
        for (LoopContext context : loopStack) {
            if (label.equals(context.label)) {
                return context;
            }
        }
        diags.addError("Unknown loop label '" + label + "'");
        return null;
    }

    private boolean patternMatchesType(AstMatchPattern pattern, TypeId targetType) {
        return switch (pattern.kind()) {
            case WILDCARD -> true;
            case INT -> targetType == TypeId.INT;
            case BOOL -> targetType == TypeId.BOOL;
            case STRING -> targetType == TypeId.STRING;
            case RANGE -> targetType == TypeId.INT;
        };
    }

    private static final class LoopContext {
        private final String label;
        private final boolean allowsValue;
        private TypeId breakType;

        private LoopContext(String label, boolean allowsValue) {
            this.label = label;
            this.allowsValue = allowsValue;
        }
    }
}
