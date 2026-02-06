package org.justlang.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.justlang.compiler.borrow.BorrowAnalyzer;
import org.justlang.compiler.borrow.BorrowValidation;
import org.justlang.compiler.borrow.LexicalBorrowAnalyzer;

public final class TypeChecker implements TypeCheckerStrategy {
    private TypeId currentReturnType = TypeId.VOID;
    private final Deque<LoopContext> loopStack = new ArrayDeque<>();
    private BorrowAnalyzer currentBorrows;

    public TypedModule typeCheck(HirModule module) {
        throw new UnsupportedOperationException("Type checker not implemented yet");
    }

    @Override
    public TypeResult typeCheck(AstModule module) {
        TypeEnvironment diagnostics = new TypeEnvironment();
        StructRegistry structs = new StructRegistry();
        EnumRegistry enums = new EnumRegistry();
        FunctionRegistry functions = new FunctionRegistry();
        boolean success = true;

        registerBuiltinEnums(enums);

        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structs.register(struct);
            }
            if (item instanceof AstEnum enumDef) {
                if (enums.contains(enumDef.name())) {
                    diagnostics.addError("Enum name is reserved or already defined: " + enumDef.name());
                    success = false;
                    continue;
                }
                enums.register(enumDef);
            }
        }

        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn) {
                if (functions.contains(fn.name())) {
                    diagnostics.addError("Duplicate function: " + fn.name());
                    success = false;
                    continue;
                }

                if ("main".equals(fn.name()) && !fn.params().isEmpty()) {
                    diagnostics.addError("main does not accept parameters");
                    success = false;
                }

                List<TypeId> paramTypes = new ArrayList<>();
                for (AstParam param : fn.params()) {
                    TypeId paramType = resolveTypeName(param.type(), structs, enums);
                    if (paramType == TypeId.UNKNOWN) {
                        diagnostics.addError("Unknown parameter type: " + param.type());
                        success = false;
                        paramTypes.add(TypeId.UNKNOWN);
                        continue;
                    }
                    if (paramType == TypeId.VOID) {
                        diagnostics.addError("Parameter type cannot be void");
                        success = false;
                        paramTypes.add(TypeId.UNKNOWN);
                        continue;
                    }
                    paramTypes.add(paramType);
                }

                TypeId returnType = resolveReturnType(fn.returnType(), structs, enums, diagnostics);
                if (returnType == TypeId.UNKNOWN) {
                    success = false;
                }
                if ("main".equals(fn.name()) && returnType != TypeId.VOID) {
                    diagnostics.addError("main must return void");
                    success = false;
                }

                functions.register(fn.name(), new FunctionRegistry.FunctionSig(fn.name(), returnType, paramTypes));
            }
        }

        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn) {
                if (!checkFunction(fn, structs, enums, functions, diagnostics)) {
                    success = false;
                }
                continue;
            }
            if (!(item instanceof AstStruct) && !(item instanceof AstEnum)) {
                diagnostics.addError("Unsupported item: " + item.getClass().getSimpleName());
                success = false;
            }
        }

        return new TypeResult(success, diagnostics);
    }

    private boolean checkFunction(AstFunction fn, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
        boolean success = true;
        TypeId expectedReturn = resolveReturnType(fn.returnType(), structs, enums, diagnostics);
        if (expectedReturn == TypeId.UNKNOWN) {
            return false;
        }

        FunctionRegistry.FunctionSig sig = functions.find(fn.name());
        if (sig == null) {
            diagnostics.addError("Unknown function signature for " + fn.name());
            return false;
        }

        TypeEnvironment locals = new TypeEnvironment();
        List<TypeId> paramTypes = sig.paramTypes();
        for (int i = 0; i < fn.params().size(); i++) {
            TypeId paramType = paramTypes.get(i);
            locals.define(fn.params().get(i).name(), paramType, fn.params().get(i).mutable());
        }

        TypeId previousReturn = currentReturnType;
        BorrowAnalyzer previousBorrows = currentBorrows;
        currentReturnType = expectedReturn;
        currentBorrows = new LexicalBorrowAnalyzer();
        if (!checkBlock(fn.body(), locals, structs, enums, functions, expectedReturn, diagnostics)) {
            success = false;
        }
        currentReturnType = previousReturn;
        currentBorrows = previousBorrows;

        if (expectedReturn != TypeId.VOID && !endsWithReturnValue(fn.body())) {
            diagnostics.addError("Non-void functions must return on all paths");
            success = false;
        }

        return success;
    }

    private boolean checkBlock(
        List<AstStmt> statements,
        TypeEnvironment locals,
        StructRegistry structs,
        EnumRegistry enums,
        FunctionRegistry functions,
        TypeId expectedReturn,
        TypeEnvironment diagnostics
    ) {
        boolean success = true;
        if (currentBorrows != null) {
            currentBorrows.enterScope();
        }
        try {
            for (AstStmt stmt : statements) {
                if (stmt instanceof AstLetStmt letStmt) {
                    if (letStmt.initializer() == null) {
                        diagnostics.addError("let without initializer is not supported yet");
                        success = false;
                        continue;
                    }
                    TypeId declaredType = null;
                    if (letStmt.type() != null) {
                        declaredType = resolveTypeName(letStmt.type(), structs, enums);
                        if (declaredType == TypeId.UNKNOWN) {
                            diagnostics.addError("Unknown type: " + letStmt.type());
                            success = false;
                            continue;
                        }
                        if (declaredType == TypeId.VOID) {
                            diagnostics.addError("let type cannot be void");
                            success = false;
                            continue;
                        }
                    }
                    TypeId exprType = inferExpr(letStmt.initializer(), locals, structs, enums, functions, diagnostics);
                    if (exprType == TypeId.UNKNOWN || exprType == TypeId.VOID) {
                        if (exprType == TypeId.VOID) {
                            diagnostics.addError("let initializer cannot be void");
                        }
                        success = false;
                        continue;
                    }
                    if (declaredType != null && !isAssignable(declaredType, exprType)) {
                        diagnostics.addError("Type mismatch in let binding '" + letStmt.name() + "': expected " + declaredType + " got " + exprType);
                        success = false;
                        continue;
                    }
                    if (!consumeMoveCandidate(letStmt.initializer(), exprType, locals, diagnostics)) {
                        success = false;
                        continue;
                    }
                    TypeId finalType = declaredType != null ? declaredType : exprType;
                    if (currentBorrows != null) {
                        currentBorrows.releaseBinding(letStmt.name());
                    }
                    locals.define(letStmt.name(), finalType, letStmt.mutable());
                    if (!registerPersistentBorrow(letStmt.name(), letStmt.initializer(), locals, diagnostics)) {
                        success = false;
                    }
                    continue;
                }
                if (stmt instanceof AstAssignStmt assignStmt) {
                    TypeEnvironment.Binding binding = locals.lookup(assignStmt.name());
                    if (binding == null) {
                        diagnostics.addError("Unknown identifier: " + assignStmt.name());
                        success = false;
                        continue;
                    }
                    String op = assignStmt.operator();
                    if (binding.moved() && !"=".equals(op)) {
                        diagnostics.addError("Use of moved value: " + assignStmt.name());
                        success = false;
                        continue;
                    }
                    if (!binding.mutable()) {
                        diagnostics.addError("Cannot assign to immutable variable: " + assignStmt.name());
                        success = false;
                        continue;
                    }
                    if (!validateBorrowOperation(assignStmt.name(), currentBorrows == null
                        ? BorrowValidation.ok()
                        : currentBorrows.validateAssignment(assignStmt.name()), diagnostics)) {
                        success = false;
                        continue;
                    }
                    TypeId valueType = inferExpr(assignStmt.value(), locals, structs, enums, functions, diagnostics);
                    if (valueType == TypeId.UNKNOWN) {
                        success = false;
                        continue;
                    }
                    if (valueType == TypeId.VOID) {
                        diagnostics.addError("assignment value cannot be void");
                        success = false;
                        continue;
                    }
                    if ("=".equals(op)) {
                        if (!isAssignable(binding.type(), valueType)) {
                            diagnostics.addError("Type mismatch in assignment to " + assignStmt.name());
                            success = false;
                        } else if (!consumeMoveCandidate(assignStmt.value(), valueType, locals, diagnostics)) {
                            success = false;
                        }
                    } else {
                        if (binding.type() != TypeId.INT || valueType != TypeId.INT) {
                            diagnostics.addError("Compound assignment requires int operands");
                            success = false;
                        }
                    }
                    if (success && "=".equals(op) && binding.type().isReference()) {
                        if (currentBorrows != null) {
                            currentBorrows.releaseBinding(assignStmt.name());
                        }
                        if (!registerPersistentBorrow(assignStmt.name(), assignStmt.value(), locals, diagnostics)) {
                            success = false;
                        }
                    }
                    if ("=".equals(op) && success) {
                        locals.clearMoved(assignStmt.name());
                    }
                    continue;
                }
            if (stmt instanceof AstExprStmt exprStmt) {
                TypeId exprType = inferExpr(exprStmt.expr(), locals, structs, enums, functions, diagnostics);
                if (exprType == TypeId.UNKNOWN) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstIfStmt ifStmt) {
                TypeId condType = inferExpr(ifStmt.condition(), locals, structs, enums, functions, diagnostics);
                if (condType != TypeId.BOOL) {
                    diagnostics.addError("if condition must be bool");
                    success = false;
                }
                TypeEnvironment thenLocals = locals.fork();
                if (!checkBlock(ifStmt.thenBranch(), thenLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                if (ifStmt.elseBranch() != null) {
                    TypeEnvironment elseLocals = locals.fork();
                    if (!checkBlock(ifStmt.elseBranch(), elseLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                        success = false;
                    }
                    locals.joinMovedFrom(thenLocals, elseLocals);
                } else {
                    locals.mergeMovedFrom(thenLocals);
                }
                continue;
            }
            if (stmt instanceof AstIfLetStmt ifLetStmt) {
                if (!checkIfLet(ifLetStmt, locals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstWhileStmt whileStmt) {
                TypeId condType = inferExpr(whileStmt.condition(), locals, structs, enums, functions, diagnostics);
                if (condType != TypeId.BOOL) {
                    diagnostics.addError("while condition must be bool");
                    success = false;
                }
                TypeEnvironment loopLocals = locals.fork();
                loopStack.push(new LoopContext(whileStmt.label(), false));
                if (!checkBlock(whileStmt.body(), loopLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                loopStack.pop();
                locals.mergeMovedFrom(loopLocals);
                continue;
            }
            if (stmt instanceof AstWhileLetStmt whileLetStmt) {
                if (!checkWhileLet(whileLetStmt, locals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstForStmt forStmt) {
                TypeId startType = inferExpr(forStmt.start(), locals, structs, enums, functions, diagnostics);
                TypeId endType = inferExpr(forStmt.end(), locals, structs, enums, functions, diagnostics);
                if (startType != TypeId.INT || endType != TypeId.INT) {
                    diagnostics.addError("for loop bounds must be int");
                    success = false;
                }
                TypeEnvironment loopLocals = locals.fork();
                loopLocals.define(forStmt.name(), TypeId.INT, false);
                loopStack.push(new LoopContext(forStmt.label(), false));
                if (!checkBlock(forStmt.body(), loopLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                loopStack.pop();
                locals.mergeMovedFrom(loopLocals);
                continue;
            }
            if (stmt instanceof AstLoopStmt loopStmt) {
                TypeEnvironment loopLocals = locals.fork();
                loopStack.push(new LoopContext(loopStmt.label(), false));
                if (!checkBlock(loopStmt.body(), loopLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                    success = false;
                }
                loopStack.pop();
                locals.mergeMovedFrom(loopLocals);
                continue;
            }
            if (stmt instanceof AstBreakStmt breakStmt) {
                if (!checkBreak(breakStmt, locals, structs, enums, functions, diagnostics)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstContinueStmt continueStmt) {
                if (!checkContinue(continueStmt, diagnostics)) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstReturnStmt returnStmt) {
                if (expectedReturn == TypeId.VOID) {
                    if (returnStmt.expr() != null) {
                        diagnostics.addError("return with value in void function");
                        success = false;
                    }
                    continue;
                }
                if (returnStmt.expr() == null) {
                    diagnostics.addError("return without value in non-void function");
                    success = false;
                    continue;
                }
                TypeId exprType = inferExpr(returnStmt.expr(), locals, structs, enums, functions, diagnostics);
                if (!isAssignable(expectedReturn, exprType)) {
                    diagnostics.addError("return type mismatch: expected " + expectedReturn + " got " + exprType);
                    success = false;
                }
                continue;
            }
            diagnostics.addError("Unsupported statement: " + stmt.getClass().getSimpleName());
            success = false;
        }
        } finally {
            if (currentBorrows != null) {
                currentBorrows.exitScope();
            }
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

    private boolean registerPersistentBorrow(String bindingName, AstExpr initializer, TypeEnvironment locals, TypeEnvironment diagnostics) {
        if (currentBorrows == null) {
            return true;
        }
        if (!(initializer instanceof AstUnaryExpr unaryExpr)) {
            return true;
        }
        if (!isBorrowOperator(unaryExpr.operator())) {
            return true;
        }
        if (!(unaryExpr.expr() instanceof AstIdentExpr identExpr)) {
            diagnostics.addError("Borrow target must be an identifier");
            return false;
        }
        TypeEnvironment.Binding targetBinding = locals.lookup(identExpr.name());
        if (targetBinding == null) {
            diagnostics.addError("Unknown identifier: " + identExpr.name());
            return false;
        }
        boolean mutableBorrow = "&mut".equals(unaryExpr.operator());
        if (!validateBorrowOperation(
            identExpr.name(),
            currentBorrows.validateBorrow(identExpr.name(), mutableBorrow),
            diagnostics
        )) {
            return false;
        }
        currentBorrows.recordBorrow(bindingName, identExpr.name(), mutableBorrow);
        return true;
    }

    private boolean isBorrowOperator(String operator) {
        return "&".equals(operator) || "&mut".equals(operator);
    }

    private boolean validateBorrowOperation(String target, BorrowValidation validation, TypeEnvironment diagnostics) {
        if (validation.allowed()) {
            return true;
        }
        if (validation.message() != null) {
            diagnostics.addError(validation.message());
        } else {
            diagnostics.addError("Borrow check failed for '" + target + "'");
        }
        return false;
    }

    private boolean consumeMoveCandidate(AstExpr expr, TypeId exprType, TypeEnvironment locals, TypeEnvironment diagnostics) {
        if (exprType == TypeId.UNKNOWN || exprType == TypeId.VOID || isCopyType(exprType)) {
            return true;
        }
        if (!(expr instanceof AstIdentExpr identExpr)) {
            return true;
        }
        TypeEnvironment.Binding binding = locals.lookup(identExpr.name());
        if (binding == null) {
            diagnostics.addError("Unknown identifier: " + identExpr.name());
            return false;
        }
        if (binding.moved()) {
            diagnostics.addError("Use of moved value: " + identExpr.name());
            return false;
        }
        if (!validateBorrowOperation(identExpr.name(), currentBorrows == null
            ? BorrowValidation.ok()
            : currentBorrows.validateMove(identExpr.name()), diagnostics)) {
            return false;
        }
        locals.markMoved(identExpr.name());
        return true;
    }

    private boolean isCopyType(TypeId type) {
        return type == TypeId.INT || type == TypeId.BOOL || type.isReference();
    }

    private boolean checkIfLet(
        AstIfLetStmt ifLetStmt,
        TypeEnvironment locals,
        StructRegistry structs,
        EnumRegistry enums,
        FunctionRegistry functions,
        TypeId expectedReturn,
        TypeEnvironment diagnostics
    ) {
        TypeId targetType = inferExpr(ifLetStmt.target(), locals, structs, enums, functions, diagnostics);
        if (targetType == TypeId.UNKNOWN) {
            return false;
        }
        if (!patternMatchesType(ifLetStmt.pattern(), targetType, enums)) {
            diagnostics.addError("if let pattern does not match target type");
            return false;
        }
        TypeEnvironment thenLocals = locals.fork();
        if (ifLetStmt.pattern().kind() == AstMatchPattern.Kind.ENUM) {
            if (!bindEnumPattern(ifLetStmt.pattern(), targetType, thenLocals, structs, enums, diagnostics)) {
                return false;
            }
        }
        boolean success = checkBlock(ifLetStmt.thenBranch(), thenLocals, structs, enums, functions, expectedReturn, diagnostics);
        if (ifLetStmt.elseBranch() != null) {
            TypeEnvironment elseLocals = locals.fork();
            if (!checkBlock(ifLetStmt.elseBranch(), elseLocals, structs, enums, functions, expectedReturn, diagnostics)) {
                success = false;
            }
            locals.joinMovedFrom(thenLocals, elseLocals);
        } else {
            locals.mergeMovedFrom(thenLocals);
        }
        return success;
    }

    private boolean checkWhileLet(
        AstWhileLetStmt whileLetStmt,
        TypeEnvironment locals,
        StructRegistry structs,
        EnumRegistry enums,
        FunctionRegistry functions,
        TypeId expectedReturn,
        TypeEnvironment diagnostics
    ) {
        TypeId targetType = inferExpr(whileLetStmt.target(), locals, structs, enums, functions, diagnostics);
        if (targetType == TypeId.UNKNOWN) {
            return false;
        }
        if (!patternMatchesType(whileLetStmt.pattern(), targetType, enums)) {
            diagnostics.addError("while let pattern does not match target type");
            return false;
        }
        TypeEnvironment bodyLocals = locals.fork();
        if (whileLetStmt.pattern().kind() == AstMatchPattern.Kind.ENUM) {
            if (!bindEnumPattern(whileLetStmt.pattern(), targetType, bodyLocals, structs, enums, diagnostics)) {
                return false;
            }
        }
        loopStack.push(new LoopContext(whileLetStmt.label(), false));
        boolean success = checkBlock(whileLetStmt.body(), bodyLocals, structs, enums, functions, expectedReturn, diagnostics);
        loopStack.pop();
        locals.mergeMovedFrom(bodyLocals);
        return success;
    }

    private TypeId inferExpr(AstExpr expr, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
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
                diagnostics.addError("Unknown identifier: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            if (binding.moved()) {
                diagnostics.addError("Use of moved value: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            return binding.type();
        }
        if (expr instanceof AstStructInitExpr initExpr) {
            StructRegistry.StructDef def = structs.find(initExpr.name());
            if (def == null) {
                diagnostics.addError("Unknown struct: " + initExpr.name());
                return TypeId.UNKNOWN;
            }
            for (AstField field : def.fields()) {
                boolean found = initExpr.fields().stream().anyMatch(f -> f.name().equals(field.name()));
                if (!found) {
                    diagnostics.addError("Missing field '" + field.name() + "' for struct " + def.name());
                    return TypeId.UNKNOWN;
                }
            }
            for (AstFieldInit field : initExpr.fields()) {
                AstField target = def.field(field.name());
                if (target == null) {
                    diagnostics.addError("Unknown field '" + field.name() + "' on struct " + def.name());
                    return TypeId.UNKNOWN;
                }
                TypeId valueType = inferExpr(field.value(), locals, structs, enums, functions, diagnostics);
                TypeId fieldType = resolveTypeName(target.type(), structs, enums);
                if (fieldType == TypeId.UNKNOWN) {
                    diagnostics.addError("Unsupported field type: " + target.type());
                    return TypeId.UNKNOWN;
                }
                if (!fieldType.equals(valueType)) {
                    diagnostics.addError("Type mismatch for field '" + field.name() + "': expected " + fieldType + " got " + valueType);
                    return TypeId.UNKNOWN;
                }
                if (!consumeMoveCandidate(field.value(), valueType, locals, diagnostics)) {
                    return TypeId.UNKNOWN;
                }
            }
            return TypeId.struct(def.name());
        }
        if (expr instanceof AstFieldAccessExpr accessExpr) {
            TypeId targetType = inferExpr(accessExpr.target(), locals, structs, enums, functions, diagnostics);
            if (!targetType.isStruct()) {
                diagnostics.addError("Field access on non-struct type: " + targetType);
                return TypeId.UNKNOWN;
            }
            StructRegistry.StructDef def = structs.find(targetType.structName());
            if (def == null) {
                diagnostics.addError("Unknown struct type: " + targetType.structName());
                return TypeId.UNKNOWN;
            }
            AstField field = def.field(accessExpr.field());
            if (field == null) {
                diagnostics.addError("Unknown field '" + accessExpr.field() + "' on struct " + def.name());
                return TypeId.UNKNOWN;
            }
            return resolveTypeName(field.type(), structs, enums);
        }
        if (expr instanceof AstBinaryExpr binaryExpr) {
            return inferBinary(binaryExpr, locals, structs, enums, functions, diagnostics);
        }
        if (expr instanceof AstUnaryExpr unaryExpr) {
            return inferUnary(unaryExpr, locals, structs, enums, functions, diagnostics);
        }
        if (expr instanceof AstCallExpr callExpr) {
            return inferCall(callExpr, locals, structs, enums, functions, diagnostics);
        }
        if (expr instanceof AstIfExpr ifExpr) {
            TypeId condType = inferExpr(ifExpr.condition(), locals, structs, enums, functions, diagnostics);
            if (condType != TypeId.BOOL) {
                diagnostics.addError("if expression condition must be bool");
                return TypeId.UNKNOWN;
            }
            TypeEnvironment thenLocals = locals.fork();
            TypeEnvironment elseLocals = locals.fork();
            TypeId thenType = inferExpr(ifExpr.thenExpr(), thenLocals, structs, enums, functions, diagnostics);
            TypeId elseType = inferExpr(ifExpr.elseExpr(), elseLocals, structs, enums, functions, diagnostics);
            locals.joinMovedFrom(thenLocals, elseLocals);
            if (thenType == TypeId.UNKNOWN || elseType == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            if (!thenType.equals(elseType)) {
                diagnostics.addError("if expression branches must match: " + thenType + " vs " + elseType);
                return TypeId.UNKNOWN;
            }
            if (thenType == TypeId.VOID) {
                diagnostics.addError("if expression cannot be void");
                return TypeId.UNKNOWN;
            }
            return thenType;
        }
        if (expr instanceof AstBlockExpr blockExpr) {
            TypeEnvironment blockLocals = locals.fork();
            if (!checkBlock(blockExpr.statements(), blockLocals, structs, enums, functions, currentReturnType, diagnostics)) {
                return TypeId.UNKNOWN;
            }
            TypeId valueType = inferExpr(blockExpr.value(), blockLocals, structs, enums, functions, diagnostics);
            if (valueType == TypeId.VOID) {
                diagnostics.addError("block expression cannot be void");
                return TypeId.UNKNOWN;
            }
            locals.adoptMovedFrom(blockLocals);
            return valueType;
        }
        if (expr instanceof AstLoopExpr loopExpr) {
            LoopContext context = new LoopContext(null, true);
            TypeEnvironment loopLocals = locals.fork();
            loopStack.push(context);
            if (!checkBlock(loopExpr.body(), loopLocals, structs, enums, functions, currentReturnType, diagnostics)) {
                loopStack.pop();
                return TypeId.UNKNOWN;
            }
            loopStack.pop();
            locals.mergeMovedFrom(loopLocals);
            if (context.breakType == null) {
                diagnostics.addError("loop expression requires break with value");
                return TypeId.UNKNOWN;
            }
            return context.breakType;
        }
        if (expr instanceof AstMatchExpr matchExpr) {
            if (matchExpr.arms().isEmpty()) {
                diagnostics.addError("match requires at least one arm");
                return TypeId.UNKNOWN;
            }
            TypeId targetType = inferExpr(matchExpr.target(), locals, structs, enums, functions, diagnostics);
            if (targetType == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            if (!consumeMoveCandidate(matchExpr.target(), targetType, locals, diagnostics)) {
                return TypeId.UNKNOWN;
            }
            if (targetType != TypeId.INT && targetType != TypeId.BOOL && targetType != TypeId.STRING && !targetType.isEnum()) {
                diagnostics.addError("match target must be int, bool, String, or enum");
                return TypeId.UNKNOWN;
            }
            boolean hasWildcard = false;
            EnumRegistry.EnumDef targetEnum = null;
            Set<String> coveredVariants = null;
            // For enum targets, track covered variants for exhaustiveness; non-enums only rely on wildcard.
            if (targetType.isEnum()) {
                targetEnum = enums.find(targetType.enumName());
                if (targetEnum == null) {
                    diagnostics.addError("Unknown enum: " + targetType.enumName());
                    return TypeId.UNKNOWN;
                }
                coveredVariants = new HashSet<>();
            }
            TypeId armType = null;
            List<TypeEnvironment> armLocalsList = new ArrayList<>();
            List<AstMatchArm> arms = matchExpr.arms();
            for (int i = 0; i < arms.size(); i++) {
                AstMatchArm arm = arms.get(i);
                AstMatchPattern pattern = arm.pattern();
                boolean hasGuard = arm.guard() != null;
                if (pattern.kind() == AstMatchPattern.Kind.WILDCARD && !hasGuard) {
                    hasWildcard = true;
                    if (i != arms.size() - 1) {
                        diagnostics.addError("wildcard '_' must be the last match arm");
                        return TypeId.UNKNOWN;
                    }
                } else {
                    if (!patternMatchesType(pattern, targetType, enums)) {
                        diagnostics.addError("match pattern does not match target type");
                        return TypeId.UNKNOWN;
                    }
                    if (pattern.kind() == AstMatchPattern.Kind.RANGE) {
                        int start = Integer.parseInt(pattern.rangeStart());
                        int end = Integer.parseInt(pattern.rangeEnd());
                        if (start > end) {
                            diagnostics.addError("match range start must be <= end");
                            return TypeId.UNKNOWN;
                        }
                    }
                    if (AstMatchPattern.Kind.ENUM.equals(pattern.kind()) && coveredVariants != null && !hasGuard) {
                        coveredVariants.add(pattern.variantName());
                    }
                }
                TypeEnvironment armLocals = locals.fork();
                armLocalsList.add(armLocals);
                if (AstMatchPattern.Kind.ENUM.equals(pattern.kind())) {
                    if (!bindEnumPattern(pattern, targetType, armLocals, structs, enums, diagnostics)) {
                        return TypeId.UNKNOWN;
                    }
                }
                if (arm.guard() != null) {
                    TypeId guardType = inferExpr(arm.guard(), armLocals, structs, enums, functions, diagnostics);
                    if (guardType != TypeId.BOOL) {
                        diagnostics.addError("match guard must be bool");
                        return TypeId.UNKNOWN;
                    }
                }
                TypeId valueType = inferExpr(arm.expr(), armLocals, structs, enums, functions, diagnostics);
                if (valueType == TypeId.UNKNOWN) {
                    return TypeId.UNKNOWN;
                }
                if (valueType == TypeId.VOID) {
                    diagnostics.addError("match arm cannot be void");
                    return TypeId.UNKNOWN;
                }
                if (armType == null) {
                    armType = valueType;
                } else if (!armType.equals(valueType)) {
                    diagnostics.addError("match arms must return the same type");
                    return TypeId.UNKNOWN;
                }
            }
            boolean enumExhaustive = targetEnum != null
                && coveredVariants != null
                && coveredVariants.size() == targetEnum.variants().size();
            boolean exhaustive = hasWildcard || enumExhaustive;
            locals.joinMovedFromAll(armLocalsList, !exhaustive);
            // Only warn when no wildcard is present; for enums, report the specific missing variants.
            if (!hasWildcard) {
                if (targetEnum != null) {
                    List<String> missing = new ArrayList<>();
                    for (AstEnumVariant variant : targetEnum.variants()) {
                        if (!coveredVariants.contains(variant.name())) {
                            missing.add(targetEnum.name() + "::" + variant.name());
                        }
                    }
                    if (!missing.isEmpty()) {
                        diagnostics.addWarning("match expression is non-exhaustive (missing " + String.join(", ", missing) + ")");
                    }
                } else {
                    diagnostics.addWarning("match expression is non-exhaustive (missing '_')");
                }
            }
            return armType == null ? TypeId.UNKNOWN : armType;
        }
        if (expr instanceof AstPathExpr pathExpr) {
            List<String> segments = pathExpr.segments();
            if (segments.size() == 2) {
                String enumName = segments.get(0);
                String variantName = segments.get(1);
                if ("Option".equals(enumName) && "None".equals(variantName)) {
                    return TypeId.option(TypeId.INFER);
                }
                if ("Result".equals(enumName) && ("Ok".equals(variantName) || "Err".equals(variantName))) {
                    diagnostics.addError("Variant '" + variantName + "' requires a value");
                    return TypeId.UNKNOWN;
                }
                EnumRegistry.EnumDef enumDef = enums.find(enumName);
                if (enumDef == null) {
                    diagnostics.addError("Unknown enum: " + enumName);
                    return TypeId.UNKNOWN;
                }
                AstEnumVariant variant = enumDef.variant(variantName);
                if (variant == null) {
                    diagnostics.addError("Unknown variant '" + variantName + "' on enum " + enumName);
                    return TypeId.UNKNOWN;
                }
                if (variant.payloadType() != null) {
                    diagnostics.addError("Variant '" + variantName + "' requires a value");
                    return TypeId.UNKNOWN;
                }
                return TypeId.enumType(enumName);
            }
            diagnostics.addError("Unsupported path expression: " + String.join("::", segments));
            return TypeId.UNKNOWN;
        }
        diagnostics.addError("Unsupported expression: " + expr.getClass().getSimpleName());
        return TypeId.UNKNOWN;
    }

    private TypeId inferCall(AstCallExpr callExpr, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
        if (isPrintCall(callExpr)) {
            if (callExpr.args().size() != 1) {
                diagnostics.addError("print expects exactly one argument");
                return TypeId.UNKNOWN;
            }
            TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, enums, functions, diagnostics);
            if (!argType.isPrintable()) {
                diagnostics.addError("print does not support type: " + argType);
                return TypeId.UNKNOWN;
            }
            return TypeId.VOID;
        }

        if (callExpr.callee().size() == 2) {
            String enumName = callExpr.callee().get(0);
            String variantName = callExpr.callee().get(1);
            if ("Option".equals(enumName)) {
                if ("Some".equals(variantName)) {
                    if (callExpr.args().size() != 1) {
                        diagnostics.addError("Variant 'Some' expects one argument");
                        return TypeId.UNKNOWN;
                    }
                    TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, enums, functions, diagnostics);
                    if (argType == TypeId.UNKNOWN || argType == TypeId.VOID) {
                        diagnostics.addError("Variant 'Some' cannot take void");
                        return TypeId.UNKNOWN;
                    }
                    if (!consumeMoveCandidate(callExpr.args().get(0), argType, locals, diagnostics)) {
                        return TypeId.UNKNOWN;
                    }
                    return TypeId.option(argType);
                }
                if ("None".equals(variantName)) {
                    if (!callExpr.args().isEmpty()) {
                        diagnostics.addError("Variant 'None' does not take a value");
                        return TypeId.UNKNOWN;
                    }
                    return TypeId.option(TypeId.INFER);
                }
            }
            if ("Result".equals(enumName)) {
                if ("Ok".equals(variantName)) {
                    if (callExpr.args().size() != 1) {
                        diagnostics.addError("Variant 'Ok' expects one argument");
                        return TypeId.UNKNOWN;
                    }
                    TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, enums, functions, diagnostics);
                    if (argType == TypeId.UNKNOWN || argType == TypeId.VOID) {
                        diagnostics.addError("Variant 'Ok' cannot take void");
                        return TypeId.UNKNOWN;
                    }
                    if (!consumeMoveCandidate(callExpr.args().get(0), argType, locals, diagnostics)) {
                        return TypeId.UNKNOWN;
                    }
                    return TypeId.result(argType, TypeId.INFER);
                }
                if ("Err".equals(variantName)) {
                    if (callExpr.args().size() != 1) {
                        diagnostics.addError("Variant 'Err' expects one argument");
                        return TypeId.UNKNOWN;
                    }
                    TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, enums, functions, diagnostics);
                    if (argType == TypeId.UNKNOWN || argType == TypeId.VOID) {
                        diagnostics.addError("Variant 'Err' cannot take void");
                        return TypeId.UNKNOWN;
                    }
                    if (!consumeMoveCandidate(callExpr.args().get(0), argType, locals, diagnostics)) {
                        return TypeId.UNKNOWN;
                    }
                    return TypeId.result(TypeId.INFER, argType);
                }
            }
            EnumRegistry.EnumDef enumDef = enums.find(enumName);
            if (enumDef == null) {
                diagnostics.addError("Unknown enum: " + enumName);
                return TypeId.UNKNOWN;
            }
            AstEnumVariant variant = enumDef.variant(variantName);
            if (variant == null) {
                diagnostics.addError("Unknown variant '" + variantName + "' on enum " + enumName);
                return TypeId.UNKNOWN;
            }
            if (variant.payloadType() == null) {
                diagnostics.addError("Variant '" + variantName + "' does not take a value");
                return TypeId.UNKNOWN;
            }
            if (callExpr.args().size() != 1) {
                diagnostics.addError("Variant '" + variantName + "' expects one argument");
                return TypeId.UNKNOWN;
            }
            TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, enums, functions, diagnostics);
            TypeId payloadType = resolveTypeName(variant.payloadType(), structs, enums);
            if (payloadType == TypeId.UNKNOWN) {
                diagnostics.addError("Unknown payload type: " + variant.payloadType());
                return TypeId.UNKNOWN;
            }
            if (payloadType != TypeId.ANY && !payloadType.equals(argType)) {
                diagnostics.addError("Variant '" + variantName + "' expects " + payloadType + " got " + argType);
                return TypeId.UNKNOWN;
            }
            if (payloadType == TypeId.ANY && argType == TypeId.VOID) {
                diagnostics.addError("Variant '" + variantName + "' cannot take void");
                return TypeId.UNKNOWN;
            }
            if (!consumeMoveCandidate(callExpr.args().get(0), argType, locals, diagnostics)) {
                return TypeId.UNKNOWN;
            }
            return TypeId.enumType(enumName);
        }

        if (callExpr.callee().size() != 1) {
            diagnostics.addError("Only direct function calls are supported");
            return TypeId.UNKNOWN;
        }

        String name = callExpr.callee().get(0);
        FunctionRegistry.FunctionSig sig = functions.find(name);
        if (sig == null) {
            diagnostics.addError("Unknown function: " + name);
            return TypeId.UNKNOWN;
        }

        if (sig.paramCount() != callExpr.args().size()) {
            diagnostics.addError("Function '" + name + "' expects " + sig.paramCount() + " arguments");
            return TypeId.UNKNOWN;
        }

        List<TypeId> paramTypes = sig.paramTypes();
        for (int i = 0; i < callExpr.args().size(); i++) {
            TypeId argType = inferExpr(callExpr.args().get(i), locals, structs, enums, functions, diagnostics);
            if (!isAssignable(paramTypes.get(i), argType)) {
                diagnostics.addError("Argument " + (i + 1) + " of '" + name + "' expected " + paramTypes.get(i) + " got " + argType);
                return TypeId.UNKNOWN;
            }
            if (!paramTypes.get(i).isReference() && !consumeMoveCandidate(callExpr.args().get(i), argType, locals, diagnostics)) {
                return TypeId.UNKNOWN;
            }
        }

        return sig.returnType();
    }

    private TypeId inferBinary(AstBinaryExpr expr, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
        TypeId left = inferExpr(expr.left(), locals, structs, enums, functions, diagnostics);
        TypeId right = inferExpr(expr.right(), locals, structs, enums, functions, diagnostics);
        String op = expr.operator();

        if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
            if (left == TypeId.INT && right == TypeId.INT) {
                return TypeId.INT;
            }
            diagnostics.addError("Arithmetic operator requires int operands");
            return TypeId.UNKNOWN;
        }

        if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
            if (left == TypeId.INT && right == TypeId.INT) {
                return TypeId.BOOL;
            }
            diagnostics.addError("Comparison operator requires int operands");
            return TypeId.UNKNOWN;
        }

        if ("==".equals(op) || "!=".equals(op)) {
            if (left.equals(right)
                && (left == TypeId.INT || left == TypeId.BOOL || left == TypeId.STRING || left == TypeId.ANY || left.isStruct() || left.isEnum())) {
                return TypeId.BOOL;
            }
            diagnostics.addError("Equality requires matching operand types");
            return TypeId.UNKNOWN;
        }

        if ("&&".equals(op) || "||".equals(op)) {
            if (left == TypeId.BOOL && right == TypeId.BOOL) {
                return TypeId.BOOL;
            }
            diagnostics.addError("Logical operator requires bool operands");
            return TypeId.UNKNOWN;
        }

        diagnostics.addError("Unsupported operator: " + op);
        return TypeId.UNKNOWN;
    }

    // Type-checks unary operators, including borrow operators used by let-bindings and expressions.
    // This method is called from inferExpr whenever the parser produces an AstUnaryExpr node.
    private TypeId inferUnary(AstUnaryExpr expr, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
        if (isBorrowOperator(expr.operator())) {
            boolean mutableBorrow = "&mut".equals(expr.operator());
            if (!(expr.expr() instanceof AstIdentExpr identExpr)) {
                diagnostics.addError("Borrow target must be an identifier");
                return TypeId.UNKNOWN;
            }
            TypeEnvironment.Binding binding = locals.lookup(identExpr.name());
            if (binding == null) {
                diagnostics.addError("Unknown identifier: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            if (mutableBorrow && !binding.mutable()) {
                diagnostics.addError("Cannot take mutable borrow of immutable variable: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            if (!validateBorrowOperation(identExpr.name(), currentBorrows == null
                ? BorrowValidation.ok()
                : currentBorrows.validateBorrow(identExpr.name(), mutableBorrow), diagnostics)) {
                return TypeId.UNKNOWN;
            }
            return TypeId.reference(binding.type(), mutableBorrow);
        }
        TypeId right = inferExpr(expr.expr(), locals, structs, enums, functions, diagnostics);
        if ("*".equals(expr.operator())) {
            if (!right.isReference()) {
                diagnostics.addError("Unary * requires reference operand");
                return TypeId.UNKNOWN;
            }
            return right.referenceInner();
        }
        if ("!".equals(expr.operator())) {
            if (right == TypeId.BOOL) {
                return TypeId.BOOL;
            }
            diagnostics.addError("Unary ! requires bool operand");
            return TypeId.UNKNOWN;
        }
        if ("-".equals(expr.operator())) {
            if (right == TypeId.INT) {
                return TypeId.INT;
            }
            diagnostics.addError("Unary - requires int operand");
            return TypeId.UNKNOWN;
        }
        diagnostics.addError("Unsupported unary operator: " + expr.operator());
        return TypeId.UNKNOWN;
    }

    private TypeId resolveReturnType(String name, StructRegistry structs, EnumRegistry enums, TypeEnvironment diagnostics) {
        if (name == null) {
            return TypeId.VOID;
        }
        TypeId type = resolveTypeName(name, structs, enums);
        if (type == TypeId.UNKNOWN) {
            diagnostics.addError("Unknown type: " + name);
        }
        return type;
    }

    private TypeId resolveTypeName(String name, StructRegistry structs, EnumRegistry enums) {
        TypeId referenceType = parseReferenceType(name, structs, enums);
        if (referenceType != null) {
            return referenceType;
        }
        TypeId genericType = parseGenericType(name, structs, enums);
        if (genericType != null) {
            return genericType;
        }
        TypeId base = TypeId.fromTypeName(name);
        if (base != TypeId.UNKNOWN) {
            return base;
        }
        if (structs.find(name) != null) {
            return TypeId.struct(name);
        }
        if (enums.find(name) != null) {
            return TypeId.enumType(name);
        }
        return TypeId.UNKNOWN;
    }

    private void registerBuiltinEnums(EnumRegistry enums) {
        List<AstEnumVariant> optionVariants = List.of(
            new AstEnumVariant("Some", "Any"),
            new AstEnumVariant("None", null)
        );
        enums.register(new AstEnum("Option", optionVariants));

        List<AstEnumVariant> resultVariants = List.of(
            new AstEnumVariant("Ok", "Any"),
            new AstEnumVariant("Err", "Any")
        );
        enums.register(new AstEnum("Result", resultVariants));
    }

    private boolean isAssignable(TypeId expected, TypeId actual) {
        if (expected == TypeId.INFER || actual == TypeId.INFER) {
            return true;
        }
        if (expected.isReference() && actual.isReference()) {
            if (!isAssignable(expected.referenceInner(), actual.referenceInner())) {
                return false;
            }
            if (expected.referenceMutable()) {
                return actual.referenceMutable();
            }
            return true;
        }
        if (expected == TypeId.ANY) {
            return actual != TypeId.VOID && actual != TypeId.UNKNOWN;
        }
        if (expected.isOption() && actual.isOption()) {
            return isAssignable(expected.optionInner(), actual.optionInner());
        }
        if (expected.isResult() && actual.isResult()) {
            return isAssignable(expected.resultOk(), actual.resultOk())
                && isAssignable(expected.resultErr(), actual.resultErr());
        }
        return expected.equals(actual);
    }

    private TypeId parseReferenceType(String name, StructRegistry structs, EnumRegistry enums) {
        String trimmed = name.trim();
        if (!trimmed.startsWith("&")) {
            return null;
        }
        boolean mutable = false;
        String innerText;
        if (trimmed.startsWith("&mut")) {
            mutable = true;
            innerText = trimmed.substring("&mut".length()).trim();
        } else {
            innerText = trimmed.substring(1).trim();
        }
        if (innerText.isEmpty()) {
            return TypeId.UNKNOWN;
        }
        TypeId innerType = resolveTypeName(innerText, structs, enums);
        if (innerType == TypeId.UNKNOWN || innerType == TypeId.VOID) {
            return TypeId.UNKNOWN;
        }
        return TypeId.reference(innerType, mutable);
    }

    private TypeId parseGenericType(String name, StructRegistry structs, EnumRegistry enums) {
        String trimmed = name.trim();
        if (trimmed.startsWith("Option<") && trimmed.endsWith(">")) {
            String innerText = trimmed.substring("Option<".length(), trimmed.length() - 1).trim();
            TypeId innerType = resolveTypeName(innerText, structs, enums);
            return innerType == TypeId.UNKNOWN ? TypeId.UNKNOWN : TypeId.option(innerType);
        }
        if (trimmed.startsWith("Result<") && trimmed.endsWith(">")) {
            String innerText = trimmed.substring("Result<".length(), trimmed.length() - 1).trim();
            int split = findTopLevelComma(innerText);
            if (split < 0) {
                return TypeId.UNKNOWN;
            }
            String okText = innerText.substring(0, split).trim();
            String errText = innerText.substring(split + 1).trim();
            TypeId okType = resolveTypeName(okText, structs, enums);
            TypeId errType = resolveTypeName(errText, structs, enums);
            if (okType == TypeId.UNKNOWN || errType == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            return TypeId.result(okType, errType);
        }
        return null;
    }

    private int findTopLevelComma(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                depth++;
                continue;
            }
            if (ch == '>') {
                depth--;
                continue;
            }
            if (ch == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPrintCall(AstCallExpr callExpr) {
        if (callExpr.callee().size() == 1) {
            return "print".equals(callExpr.callee().get(0));
        }
        return callExpr.callee().size() == 2
            && "std".equals(callExpr.callee().get(0))
            && "print".equals(callExpr.callee().get(1));
    }

    private boolean checkBreak(AstBreakStmt breakStmt, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, FunctionRegistry functions, TypeEnvironment diagnostics) {
        LoopContext context = resolveLoopContext(breakStmt.label(), diagnostics, "break");
        if (context == null) {
            return false;
        }
        if (breakStmt.expr() != null) {
            if (!context.allowsValue) {
                diagnostics.addError("break with value is only allowed in loop expressions");
                return false;
            }
            TypeId valueType = inferExpr(breakStmt.expr(), locals, structs, enums, functions, diagnostics);
            if (valueType == TypeId.UNKNOWN || valueType == TypeId.VOID) {
                diagnostics.addError("break value must be a non-void expression");
                return false;
            }
            if (context.breakType == null) {
                context.breakType = valueType;
            } else if (!isAssignable(context.breakType, valueType)) {
                diagnostics.addError("break values in loop expression must have the same type");
                return false;
            }
            return true;
        }

        if (context.allowsValue) {
            diagnostics.addError("break value required for loop expression");
            return false;
        }
        return true;
    }

    private boolean checkContinue(AstContinueStmt continueStmt, TypeEnvironment diagnostics) {
        LoopContext context = resolveLoopContext(continueStmt.label(), diagnostics, "continue");
        return context != null;
    }

    private LoopContext resolveLoopContext(String label, TypeEnvironment diagnostics, String keyword) {
        if (loopStack.isEmpty()) {
            diagnostics.addError(keyword + " is only valid inside loops");
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
        diagnostics.addError("Unknown loop label '" + label + "'");
        return null;
    }

    private boolean patternMatchesType(AstMatchPattern pattern, TypeId targetType, EnumRegistry enums) {
        if (pattern.kind() == AstMatchPattern.Kind.ENUM && targetType.isOption()) {
            return "Option".equals(pattern.enumName())
                && ("Some".equals(pattern.variantName()) || "None".equals(pattern.variantName()));
        }
        if (pattern.kind() == AstMatchPattern.Kind.ENUM && targetType.isResult()) {
            return "Result".equals(pattern.enumName())
                && ("Ok".equals(pattern.variantName()) || "Err".equals(pattern.variantName()));
        }
        return switch (pattern.kind()) {
            case WILDCARD -> true;
            case INT -> targetType == TypeId.INT;
            case BOOL -> targetType == TypeId.BOOL;
            case STRING -> targetType == TypeId.STRING;
            case RANGE -> targetType == TypeId.INT;
            case ENUM -> {
                if (!targetType.isEnum()) {
                    yield false;
                }
                if (!targetType.enumName().equals(pattern.enumName())) {
                    yield false;
                }
                EnumRegistry.EnumDef def = enums.find(targetType.enumName());
                if (def == null) {
                    yield false;
                }
                AstEnumVariant variant = def.variant(pattern.variantName());
                yield variant != null;
            }
        };
    }

    private boolean bindEnumPattern(AstMatchPattern pattern, TypeId targetType, TypeEnvironment locals, StructRegistry structs, EnumRegistry enums, TypeEnvironment diagnostics) {
        if (targetType.isOption()) {
            if (!"Option".equals(pattern.enumName())) {
                diagnostics.addError("enum pattern does not match target enum");
                return false;
            }
            if ("None".equals(pattern.variantName())) {
                if (pattern.binding() != null) {
                    diagnostics.addError("Variant 'None' does not bind a value");
                    return false;
                }
                return true;
            }
            if (!"Some".equals(pattern.variantName())) {
                diagnostics.addError("Unknown variant '" + pattern.variantName() + "' on enum Option");
                return false;
            }
            if (pattern.binding() != null) {
                locals.define(pattern.binding(), targetType.optionInner(), false);
            }
            return true;
        }
        if (targetType.isResult()) {
            if (!"Result".equals(pattern.enumName())) {
                diagnostics.addError("enum pattern does not match target enum");
                return false;
            }
            if ("Ok".equals(pattern.variantName())) {
                if (pattern.binding() != null) {
                    locals.define(pattern.binding(), targetType.resultOk(), false);
                }
                return true;
            }
            if ("Err".equals(pattern.variantName())) {
                if (pattern.binding() != null) {
                    locals.define(pattern.binding(), targetType.resultErr(), false);
                }
                return true;
            }
            diagnostics.addError("Unknown variant '" + pattern.variantName() + "' on enum Result");
            return false;
        }
        if (!targetType.isEnum()) {
            diagnostics.addError("enum pattern used on non-enum type");
            return false;
        }
        if (!targetType.enumName().equals(pattern.enumName())) {
            diagnostics.addError("enum pattern does not match target enum");
            return false;
        }
        EnumRegistry.EnumDef def = enums.find(targetType.enumName());
        if (def == null) {
            diagnostics.addError("Unknown enum: " + targetType.enumName());
            return false;
        }
        AstEnumVariant variant = def.variant(pattern.variantName());
        if (variant == null) {
            diagnostics.addError("Unknown variant '" + pattern.variantName() + "' on enum " + def.name());
            return false;
        }
        if (pattern.binding() == null) {
            return true;
        }
        if (variant.payloadType() == null) {
            diagnostics.addError("Variant '" + variant.name() + "' does not bind a value");
            return false;
        }
        TypeId payloadType = resolveTypeName(variant.payloadType(), structs, enums);
        if (payloadType == TypeId.UNKNOWN) {
            diagnostics.addError("Unknown payload type: " + variant.payloadType());
            return false;
        }
        locals.define(pattern.binding(), payloadType, false);
        return true;
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
