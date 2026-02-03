package org.justlang.compiler;

import java.util.List;

public final class TypeChecker {
    public TypedModule typeCheck(HirModule module) {
        throw new UnsupportedOperationException("Type checker not implemented yet");
    }

    public TypeResult typeCheck(AstModule module) {
        TypeEnvironment env = new TypeEnvironment();
        StructRegistry structs = new StructRegistry();
        boolean success = true;

        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structs.register(struct);
            }
        }

        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn) {
                TypeEnvironment functionEnv = new TypeEnvironment();
                for (AstStmt stmt : fn.body()) {
                    if (stmt instanceof AstIfStmt ifStmt) {
                        TypeId condType = inferExpr(ifStmt.condition(), functionEnv, structs, env);
                        if (condType != TypeId.BOOL) {
                            env.addError("if condition must be bool");
                            success = false;
                        }
                        TypeEnvironment thenEnv = functionEnv.fork();
                        if (!checkBlock(ifStmt.thenBranch(), thenEnv, structs, env)) {
                            success = false;
                        }
                        if (ifStmt.elseBranch() != null) {
                            TypeEnvironment elseEnv = functionEnv.fork();
                            if (!checkBlock(ifStmt.elseBranch(), elseEnv, structs, env)) {
                                success = false;
                            }
                        }
                        continue;
                    }
                    if (stmt instanceof AstReturnStmt returnStmt) {
                        if (returnStmt.expr() != null) {
                            env.addError("return with value is not supported yet");
                            success = false;
                        }
                        continue;
                    }
                    if (stmt instanceof AstLetStmt letStmt) {
                        if (letStmt.initializer() == null) {
                            env.addError("let without initializer is not supported yet");
                            success = false;
                            continue;
                        }
                        TypeId exprType = inferExpr(letStmt.initializer(), functionEnv, structs, env);
                        if (exprType == TypeId.UNKNOWN) {
                            success = false;
                            continue;
                        }
                        functionEnv.define(letStmt.name(), exprType);
                        continue;
                    }
                    if (stmt instanceof AstExprStmt exprStmt) {
                        TypeId exprType = inferExpr(exprStmt.expr(), functionEnv, structs, env);
                        if (exprType == TypeId.UNKNOWN) {
                            success = false;
                        }
                        continue;
                    }
                    env.addError("Unsupported statement: " + stmt.getClass().getSimpleName());
                    success = false;
                }
                continue;
            }
            if (!(item instanceof AstStruct)) {
                env.addError("Unsupported item: " + item.getClass().getSimpleName());
                success = false;
            }
        }

        return new TypeResult(success, env);
    }

    private boolean checkBlock(List<AstStmt> statements, TypeEnvironment locals, StructRegistry structs, TypeEnvironment diags) {
        boolean success = true;
        for (AstStmt stmt : statements) {
            if (stmt instanceof AstLetStmt letStmt) {
                if (letStmt.initializer() == null) {
                    diags.addError("let without initializer is not supported yet");
                    success = false;
                    continue;
                }
                TypeId exprType = inferExpr(letStmt.initializer(), locals, structs, diags);
                if (exprType == TypeId.UNKNOWN) {
                    success = false;
                    continue;
                }
                locals.define(letStmt.name(), exprType);
                continue;
            }
            if (stmt instanceof AstExprStmt exprStmt) {
                TypeId exprType = inferExpr(exprStmt.expr(), locals, structs, diags);
                if (exprType == TypeId.UNKNOWN) {
                    success = false;
                }
                continue;
            }
            if (stmt instanceof AstIfStmt ifStmt) {
                TypeId condType = inferExpr(ifStmt.condition(), locals, structs, diags);
                if (condType != TypeId.BOOL) {
                    diags.addError("if condition must be bool");
                    success = false;
                }
                if (!checkBlock(ifStmt.thenBranch(), locals.fork(), structs, diags)) {
                    success = false;
                }
                if (ifStmt.elseBranch() != null) {
                    if (!checkBlock(ifStmt.elseBranch(), locals.fork(), structs, diags)) {
                        success = false;
                    }
                }
                continue;
            }
            if (stmt instanceof AstReturnStmt returnStmt) {
                if (returnStmt.expr() != null) {
                    diags.addError("return with value is not supported yet");
                    success = false;
                }
                continue;
            }
            diags.addError("Unsupported statement: " + stmt.getClass().getSimpleName());
            success = false;
        }
        return success;
    }

    private TypeId inferExpr(AstExpr expr, TypeEnvironment locals, StructRegistry structs, TypeEnvironment diags) {
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
            TypeId type = locals.lookup(identExpr.name());
            if (type == null) {
                diags.addError("Unknown identifier: " + identExpr.name());
                return TypeId.UNKNOWN;
            }
            return type;
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
                TypeId valueType = inferExpr(field.value(), locals, structs, diags);
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
            TypeId targetType = inferExpr(accessExpr.target(), locals, structs, diags);
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
            return inferBinary(binaryExpr, locals, structs, diags);
        }
        if (expr instanceof AstUnaryExpr unaryExpr) {
            return inferUnary(unaryExpr, locals, structs, diags);
        }
        if (expr instanceof AstCallExpr callExpr) {
            return inferCall(callExpr, locals, structs, diags);
        }
        diags.addError("Unsupported expression: " + expr.getClass().getSimpleName());
        return TypeId.UNKNOWN;
    }

    private TypeId inferCall(AstCallExpr callExpr, TypeEnvironment locals, StructRegistry structs, TypeEnvironment diags) {
        if (!isPrintCall(callExpr)) {
            diags.addError("Unsupported call: " + String.join("::", callExpr.callee()));
            return TypeId.UNKNOWN;
        }
        if (callExpr.args().size() != 1) {
            diags.addError("print expects exactly one argument");
            return TypeId.UNKNOWN;
        }
        TypeId argType = inferExpr(callExpr.args().get(0), locals, structs, diags);
        if (!argType.isPrintable()) {
            diags.addError("print does not support type: " + argType);
            return TypeId.UNKNOWN;
        }
        return TypeId.VOID;
    }

    private TypeId inferBinary(AstBinaryExpr expr, TypeEnvironment locals, StructRegistry structs, TypeEnvironment diags) {
        TypeId left = inferExpr(expr.left(), locals, structs, diags);
        TypeId right = inferExpr(expr.right(), locals, structs, diags);
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

    private TypeId inferUnary(AstUnaryExpr expr, TypeEnvironment locals, StructRegistry structs, TypeEnvironment diags) {
        TypeId right = inferExpr(expr.expr(), locals, structs, diags);
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
}
