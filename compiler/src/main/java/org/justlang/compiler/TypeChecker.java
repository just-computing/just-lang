package org.justlang.compiler;

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
            for (AstFieldInit field : initExpr.fields()) {
                AstField target = def.field(field.name());
                if (target == null) {
                    diags.addError("Unknown field '" + field.name() + "' on struct " + def.name());
                    return TypeId.UNKNOWN;
                }
                TypeId valueType = inferExpr(field.value(), locals, structs, diags);
                TypeId fieldType = TypeId.fromTypeName(target.type());
                if (fieldType == TypeId.UNKNOWN) {
                    diags.addError("Unsupported field type: " + target.type());
                    return TypeId.UNKNOWN;
                }
                if (valueType != fieldType) {
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
            return TypeId.fromTypeName(field.type());
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

    private boolean isPrintCall(AstCallExpr callExpr) {
        if (callExpr.callee().size() == 1) {
            return "print".equals(callExpr.callee().get(0));
        }
        return callExpr.callee().size() == 2
            && "std".equals(callExpr.callee().get(0))
            && "print".equals(callExpr.callee().get(1));
    }
}
