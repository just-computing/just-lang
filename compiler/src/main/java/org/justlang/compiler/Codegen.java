package org.justlang.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Codegen {
    private static final String MAIN_CLASS_NAME = "Main";
    private static final String MAIN_INTERNAL_NAME = "Main";
    private final Map<String, StructLayout> structLayouts = new HashMap<>();
    private final Map<String, FunctionInfo> functions = new HashMap<>();
    private final Deque<LoopLabels> loopStack = new ArrayDeque<>();
    private ReturnInfo currentReturnInfo;

    public List<ClassFile> emit(AstModule module) {
        buildStructLayouts(module);
        buildFunctionRegistry(module);
        List<ClassFile> classFiles = new ArrayList<>();
        for (StructLayout layout : structLayouts.values()) {
            classFiles.add(emitStructClass(layout));
        }
        classFiles.add(emitMainClass(module));
        return classFiles;
    }

    public String mainClassName() {
        return MAIN_CLASS_NAME;
    }

    private ClassFile emitMainClass(AstModule module) {
        AstFunction main = findMain(module);
        if (main == null) {
            throw new IllegalStateException("Missing `fn main()`");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, MAIN_INTERNAL_NAME, null, "java/lang/Object", null);

        emitDefaultConstructor(writer);
        emitFunctions(writer, module);
        emitMainMethod(writer, main);

        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        return new ClassFile(MAIN_CLASS_NAME, bytes);
    }

    private ClassFile emitStructClass(StructLayout layout) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, layout.internalName(), null, "java/lang/Object", null);

        for (FieldInfo field : layout.fields()) {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, field.name(), field.descriptor(), null, null).visitEnd();
        }

        emitStructConstructor(writer, layout);
        emitStructToString(writer, layout);
        writer.visitEnd();
        return new ClassFile(layout.internalName(), writer.toByteArray());
    }

    private void emitDefaultConstructor(ClassWriter writer) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitStructConstructor(ClassWriter writer, StructLayout layout) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", layout.constructorDescriptor(), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        int slot = 1;
        for (FieldInfo field : layout.fields()) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            loadLocal(mv, field.kind(), slot);
            mv.visitFieldInsn(Opcodes.PUTFIELD, layout.internalName(), field.name(), field.descriptor());
            slot += 1;
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitStructToString(ClassWriter writer, StructLayout layout) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();

        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(layout.name() + "{");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);

        int index = 0;
        for (FieldInfo field : layout.fields()) {
            if (index > 0) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            mv.visitLdcInsn(field.name() + "=");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, layout.internalName(), field.name(), field.descriptor());

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", field.appendDescriptor(), false);
            index++;
        }

        mv.visitLdcInsn("}");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitFunctions(ClassWriter writer, AstModule module) {
        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn && !"main".equals(fn.name())) {
                emitFunction(writer, fn);
            }
        }
    }

    private void emitFunction(ClassWriter writer, AstFunction fn) {
        FunctionInfo info = functions.get(fn.name());
        if (info == null) {
            throw new IllegalStateException("Unknown function: " + fn.name());
        }
        MethodVisitor mv = writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            fn.name(),
            info.descriptor(),
            null,
            null
        );
        mv.visitCode();
        ReturnInfo returnInfo = new ReturnInfo(info.returnKind(), info.returnStructName());
        ReturnInfo previousReturn = currentReturnInfo;
        currentReturnInfo = returnInfo;
        LocalState locals = new LocalState(info.paramCount());
        int slot = 0;
        for (ParamInfo param : info.params()) {
            locals.define(param.name(), param.kind(), param.structName(), slot);
            slot += 1;
        }
        emitBlock(mv, fn.body(), locals, returnInfo);
        currentReturnInfo = previousReturn;
        if (returnInfo.kind() == ValueKind.VOID) {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitMainMethod(ClassWriter writer, AstFunction main) {
        if (!main.params().isEmpty()) {
            throw new IllegalStateException("main() parameters are not supported yet");
        }

        MethodVisitor mv = writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        );
        mv.visitCode();

        LocalState locals = new LocalState(1);
        ReturnInfo returnInfo = new ReturnInfo(ValueKind.VOID, null);
        ReturnInfo previousReturn = currentReturnInfo;
        currentReturnInfo = returnInfo;
        emitBlock(mv, main.body(), locals, returnInfo);
        currentReturnInfo = previousReturn;

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitBlock(MethodVisitor mv, List<AstStmt> statements, LocalState locals, ReturnInfo returnInfo) {
        for (AstStmt stmt : statements) {
            emitStatement(mv, stmt, locals, returnInfo);
        }
    }

    private void emitStatement(MethodVisitor mv, AstStmt stmt, LocalState locals, ReturnInfo returnInfo) {
        if (stmt instanceof AstIfStmt ifStmt) {
            emitIf(mv, ifStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstWhileStmt whileStmt) {
            emitWhile(mv, whileStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstBreakStmt) {
            emitBreak(mv);
            return;
        }
        if (stmt instanceof AstContinueStmt) {
            emitContinue(mv);
            return;
        }
        if (stmt instanceof AstReturnStmt returnStmt) {
            emitReturn(mv, returnStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstLetStmt letStmt) {
            if (letStmt.initializer() == null) {
                throw new IllegalStateException("let without initializer is not supported");
            }
            ExprValue value = emitExpr(mv, letStmt.initializer(), locals);
            if (value.kind() == ValueKind.VOID) {
                throw new IllegalStateException("Cannot assign void expression to let binding");
            }
            int slot = locals.allocate(letStmt.name(), value.kind(), value.structName());
            storeLocal(mv, value.kind(), slot);
            return;
        }
        if (stmt instanceof AstExprStmt exprStmt) {
            ExprValue value = emitExpr(mv, exprStmt.expr(), locals);
            if (value.kind() != ValueKind.VOID) {
                mv.visitInsn(Opcodes.POP);
            }
            return;
        }
        throw new IllegalStateException("Unsupported statement: " + stmt.getClass().getSimpleName());
    }

    private void emitIf(MethodVisitor mv, AstIfStmt ifStmt, LocalState locals, ReturnInfo returnInfo) {
        ExprValue condition = emitExpr(mv, ifStmt.condition(), locals);
        if (condition.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("if condition must be bool");
        }
        Label elseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
        emitBlock(mv, ifStmt.thenBranch(), locals.fork(), returnInfo);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(elseLabel);
        if (ifStmt.elseBranch() != null) {
            emitBlock(mv, ifStmt.elseBranch(), locals.fork(), returnInfo);
        }
        mv.visitLabel(endLabel);
    }

    private void emitWhile(MethodVisitor mv, AstWhileStmt whileStmt, LocalState locals, ReturnInfo returnInfo) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        mv.visitLabel(startLabel);
        ExprValue condition = emitExpr(mv, whileStmt.condition(), locals);
        if (condition.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("while condition must be bool");
        }
        mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
        loopStack.push(new LoopLabels(startLabel, endLabel));
        emitBlock(mv, whileStmt.body(), locals.fork(), returnInfo);
        loopStack.pop();
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitBreak(MethodVisitor mv) {
        LoopLabels labels = loopStack.peek();
        if (labels == null) {
            throw new IllegalStateException("break is only valid inside loops");
        }
        mv.visitJumpInsn(Opcodes.GOTO, labels.breakLabel());
    }

    private void emitContinue(MethodVisitor mv) {
        LoopLabels labels = loopStack.peek();
        if (labels == null) {
            throw new IllegalStateException("continue is only valid inside loops");
        }
        mv.visitJumpInsn(Opcodes.GOTO, labels.continueLabel());
    }

    private void emitReturn(MethodVisitor mv, AstReturnStmt returnStmt, LocalState locals, ReturnInfo returnInfo) {
        if (returnInfo.kind() == ValueKind.VOID) {
            if (returnStmt.expr() != null) {
                throw new IllegalStateException("Return with value in void function");
            }
            mv.visitInsn(Opcodes.RETURN);
            return;
        }
        if (returnStmt.expr() == null) {
            throw new IllegalStateException("Missing return value in non-void function");
        }
        ExprValue value = emitExpr(mv, returnStmt.expr(), locals);
        if (!value.matches(returnInfo)) {
            throw new IllegalStateException("Return type mismatch");
        }
        mv.visitInsn(switch (returnInfo.kind()) {
            case INT, BOOL -> Opcodes.IRETURN;
            case STRING, STRUCT -> Opcodes.ARETURN;
            case VOID -> Opcodes.RETURN;
        });
    }

    private ExprValue emitIfExpr(MethodVisitor mv, AstIfExpr ifExpr, LocalState locals) {
        ExprValue condition = emitExpr(mv, ifExpr.condition(), locals);
        if (condition.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("if expression condition must be bool");
        }
        Label elseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
        ExprValue thenValue = emitExpr(mv, ifExpr.thenExpr(), locals);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(elseLabel);
        ExprValue elseValue = emitExpr(mv, ifExpr.elseExpr(), locals);
        if (!thenValue.matches(new ReturnInfo(elseValue.kind(), elseValue.structName()))) {
            throw new IllegalStateException("if expression branches must return the same type");
        }
        if (thenValue.kind() == ValueKind.VOID) {
            throw new IllegalStateException("if expression cannot be void");
        }
        mv.visitLabel(endLabel);
        return thenValue;
    }

    private ExprValue emitBlockExpr(MethodVisitor mv, AstBlockExpr blockExpr, LocalState locals) {
        if (currentReturnInfo == null) {
            throw new IllegalStateException("Missing return context for block expression");
        }
        LocalState blockLocals = locals.fork();
        for (AstStmt stmt : blockExpr.statements()) {
            emitStatement(mv, stmt, blockLocals, currentReturnInfo);
        }
        ExprValue value = emitExpr(mv, blockExpr.value(), blockLocals);
        if (value.kind() == ValueKind.VOID) {
            throw new IllegalStateException("block expression cannot be void");
        }
        return value;
    }

    private ExprValue emitExpr(MethodVisitor mv, AstExpr expr, LocalState locals) {
        if (expr instanceof AstStringExpr stringExpr) {
            mv.visitLdcInsn(stringExpr.literal());
            return ExprValue.of(ValueKind.STRING);
        }
        if (expr instanceof AstBoolExpr boolExpr) {
            mv.visitInsn(boolExpr.value() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            return ExprValue.of(ValueKind.BOOL);
        }
        if (expr instanceof AstNumberExpr numberExpr) {
            int value = Integer.parseInt(numberExpr.literal());
            mv.visitLdcInsn(value);
            return ExprValue.of(ValueKind.INT);
        }
        if (expr instanceof AstIdentExpr identExpr) {
            Local local = locals.get(identExpr.name());
            if (local == null) {
                throw new IllegalStateException("Unknown identifier: " + identExpr.name());
            }
            loadLocal(mv, local.kind(), local.slot());
            return new ExprValue(local.kind(), local.structName());
        }
        if (expr instanceof AstStructInitExpr initExpr) {
            return emitStructInit(mv, initExpr, locals);
        }
        if (expr instanceof AstFieldAccessExpr accessExpr) {
            return emitFieldAccess(mv, accessExpr, locals);
        }
        if (expr instanceof AstBinaryExpr binaryExpr) {
            return emitBinary(mv, binaryExpr, locals);
        }
        if (expr instanceof AstUnaryExpr unaryExpr) {
            return emitUnary(mv, unaryExpr, locals);
        }
        if (expr instanceof AstCallExpr callExpr) {
            return emitCall(mv, callExpr, locals);
        }
        if (expr instanceof AstIfExpr ifExpr) {
            return emitIfExpr(mv, ifExpr, locals);
        }
        if (expr instanceof AstBlockExpr blockExpr) {
            return emitBlockExpr(mv, blockExpr, locals);
        }
        throw new IllegalStateException("Unsupported expression: " + expr.getClass().getSimpleName());
    }

    private ExprValue emitCall(MethodVisitor mv, AstCallExpr call, LocalState locals) {
        if (!isPrintCall(call)) {
            if (call.callee().size() != 1) {
                throw new IllegalStateException("Only direct function calls are supported");
            }
            String name = call.callee().get(0);
            FunctionInfo info = functions.get(name);
            if (info == null) {
                throw new IllegalStateException("Unknown function: " + name);
            }
            if (call.args().size() != info.paramCount()) {
                throw new IllegalStateException("Function '" + name + "' expects " + info.paramCount() + " arguments");
            }
            for (int i = 0; i < call.args().size(); i++) {
                ExprValue arg = emitExpr(mv, call.args().get(i), locals);
                ParamInfo param = info.params().get(i);
                if (!arg.matches(new ReturnInfo(param.kind(), param.structName()))) {
                    throw new IllegalStateException("Argument " + (i + 1) + " type mismatch for function " + name);
                }
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, MAIN_INTERNAL_NAME, name, info.descriptor(), false);
            return new ExprValue(info.returnKind(), info.returnStructName());
        }
        if (call.args().size() != 1) {
            throw new IllegalStateException("print expects exactly one argument");
        }
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        ExprValue arg = emitExpr(mv, call.args().get(0), locals);
        if (arg.kind() == ValueKind.STRING) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        } else if (arg.kind() == ValueKind.INT) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        } else if (arg.kind() == ValueKind.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
        } else if (arg.kind() == ValueKind.STRUCT) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
        } else {
            throw new IllegalStateException("Unsupported print argument type: " + arg.kind());
        }
        return ExprValue.of(ValueKind.VOID);
    }

    private ExprValue emitStructInit(MethodVisitor mv, AstStructInitExpr initExpr, LocalState locals) {
        StructLayout layout = structLayouts.get(initExpr.name());
        if (layout == null) {
            throw new IllegalStateException("Unknown struct: " + initExpr.name());
        }
        mv.visitTypeInsn(Opcodes.NEW, layout.internalName());
        mv.visitInsn(Opcodes.DUP);
        for (FieldInfo field : layout.fields()) {
            AstFieldInit init = initExpr.fields().stream()
                .filter(f -> f.name().equals(field.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing field: " + field.name()));
            ExprValue value = emitExpr(mv, init.value(), locals);
            if (!layout.matches(field, value.kind(), value.structName())) {
                throw new IllegalStateException("Field type mismatch for " + field.name());
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, layout.internalName(), "<init>", layout.constructorDescriptor(), false);
        return new ExprValue(ValueKind.STRUCT, layout.name());
    }

    private ExprValue emitFieldAccess(MethodVisitor mv, AstFieldAccessExpr accessExpr, LocalState locals) {
        ExprValue target = emitExpr(mv, accessExpr.target(), locals);
        if (target.kind() != ValueKind.STRUCT || target.structName() == null) {
            throw new IllegalStateException("Field access on non-struct type");
        }
        StructLayout layout = structLayouts.get(target.structName());
        if (layout == null) {
            throw new IllegalStateException("Unknown struct: " + target.structName());
        }
        FieldInfo field = layout.field(accessExpr.field());
        if (field == null) {
            throw new IllegalStateException("Unknown field: " + accessExpr.field());
        }
        mv.visitFieldInsn(Opcodes.GETFIELD, layout.internalName(), field.name(), field.descriptor());
        return new ExprValue(field.kind(), field.structName());
    }

    private ExprValue emitUnary(MethodVisitor mv, AstUnaryExpr unaryExpr, LocalState locals) {
        ExprValue right = emitExpr(mv, unaryExpr.expr(), locals);
        if ("!".equals(unaryExpr.operator())) {
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IXOR);
            return ExprValue.of(ValueKind.BOOL);
        }
        if ("-".equals(unaryExpr.operator())) {
            mv.visitInsn(Opcodes.INEG);
            return ExprValue.of(ValueKind.INT);
        }
        throw new IllegalStateException("Unsupported unary operator: " + unaryExpr.operator());
    }

    private ExprValue emitBinary(MethodVisitor mv, AstBinaryExpr binaryExpr, LocalState locals) {
        String op = binaryExpr.operator();
        if ("&&".equals(op)) {
            return emitLogicalAnd(mv, binaryExpr.left(), binaryExpr.right(), locals);
        }
        if ("||".equals(op)) {
            return emitLogicalOr(mv, binaryExpr.left(), binaryExpr.right(), locals);
        }
        ExprValue left = emitExpr(mv, binaryExpr.left(), locals);
        ExprValue right = emitExpr(mv, binaryExpr.right(), locals);

        if ("+".equals(op)) {
            mv.visitInsn(Opcodes.IADD);
            return ExprValue.of(ValueKind.INT);
        }
        if ("-".equals(op)) {
            mv.visitInsn(Opcodes.ISUB);
            return ExprValue.of(ValueKind.INT);
        }
        if ("*".equals(op)) {
            mv.visitInsn(Opcodes.IMUL);
            return ExprValue.of(ValueKind.INT);
        }
        if ("/".equals(op)) {
            mv.visitInsn(Opcodes.IDIV);
            return ExprValue.of(ValueKind.INT);
        }
        if ("==".equals(op) || "!=".equals(op)) {
            return emitEquality(mv, op, left);
        }
        if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
            return emitComparison(mv, op);
        }
        throw new IllegalStateException("Unsupported binary operator: " + op);
    }

    private ExprValue emitLogicalAnd(MethodVisitor mv, AstExpr leftExpr, AstExpr rightExpr, LocalState locals) {
        Label falseLabel = new Label();
        Label endLabel = new Label();
        ExprValue left = emitExpr(mv, leftExpr, locals);
        if (left.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("Logical && requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        ExprValue right = emitExpr(mv, rightExpr, locals);
        if (right.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("Logical && requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(falseLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitLabel(endLabel);
        return ExprValue.of(ValueKind.BOOL);
    }

    private ExprValue emitLogicalOr(MethodVisitor mv, AstExpr leftExpr, AstExpr rightExpr, LocalState locals) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        ExprValue left = emitExpr(mv, leftExpr, locals);
        if (left.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("Logical || requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        ExprValue right = emitExpr(mv, rightExpr, locals);
        if (right.kind() != ValueKind.BOOL) {
            throw new IllegalStateException("Logical || requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
        return ExprValue.of(ValueKind.BOOL);
    }

    private ExprValue emitEquality(MethodVisitor mv, String op, ExprValue left) {
        boolean negate = "!=".equals(op);
        if (left.kind() == ValueKind.STRING) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            if (negate) {
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IXOR);
            }
            return ExprValue.of(ValueKind.BOOL);
        }
        int opcode;
        if (left.kind() == ValueKind.STRUCT) {
            opcode = negate ? Opcodes.IF_ACMPNE : Opcodes.IF_ACMPEQ;
        } else {
            opcode = negate ? Opcodes.IF_ICMPNE : Opcodes.IF_ICMPEQ;
        }
        return emitBooleanJump(mv, opcode);
    }

    private ExprValue emitComparison(MethodVisitor mv, String op) {
        int opcode = switch (op) {
            case "<" -> Opcodes.IF_ICMPLT;
            case "<=" -> Opcodes.IF_ICMPLE;
            case ">" -> Opcodes.IF_ICMPGT;
            case ">=" -> Opcodes.IF_ICMPGE;
            default -> throw new IllegalStateException("Unsupported comparison: " + op);
        };
        return emitBooleanJump(mv, opcode);
    }

    private ExprValue emitBooleanJump(MethodVisitor mv, int opcode) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(opcode, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
        return ExprValue.of(ValueKind.BOOL);
    }

    private void storeLocal(MethodVisitor mv, ValueKind kind, int slot) {
        if (kind == ValueKind.INT || kind == ValueKind.BOOL) {
            mv.visitVarInsn(Opcodes.ISTORE, slot);
            return;
        }
        if (kind == ValueKind.STRING || kind == ValueKind.STRUCT) {
            mv.visitVarInsn(Opcodes.ASTORE, slot);
            return;
        }
        throw new IllegalStateException("Unsupported local type: " + kind);
    }

    private void loadLocal(MethodVisitor mv, ValueKind kind, int slot) {
        if (kind == ValueKind.INT || kind == ValueKind.BOOL) {
            mv.visitVarInsn(Opcodes.ILOAD, slot);
            return;
        }
        if (kind == ValueKind.STRING || kind == ValueKind.STRUCT) {
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            return;
        }
        throw new IllegalStateException("Unsupported local type: " + kind);
    }

    private boolean isPrintCall(AstCallExpr call) {
        List<String> callee = call.callee();
        if (callee.size() == 1 && "print".equals(callee.get(0))) {
            return true;
        }
        return callee.size() == 2 && "std".equals(callee.get(0)) && "print".equals(callee.get(1));
    }

    private AstFunction findMain(AstModule module) {
        AstFunction main = null;
        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn && "main".equals(fn.name())) {
                if (main != null) {
                    throw new IllegalStateException("Multiple `fn main()` definitions found");
                }
                main = fn;
            }
        }
        return main;
    }

    private void buildFunctionRegistry(AstModule module) {
        for (AstItem item : module.items()) {
            if (item instanceof AstFunction fn && !"main".equals(fn.name())) {
                if (functions.containsKey(fn.name())) {
                    throw new IllegalStateException("Duplicate function: " + fn.name());
                }
                TypeId returnType = resolveReturnType(fn.returnType());
                FunctionInfo info = functionInfoFrom(fn, returnType);
                functions.put(fn.name(), info);
            }
        }
    }

    private void buildStructLayouts(AstModule module) {
        Set<String> structNames = new HashSet<>();
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structNames.add(struct.name());
            }
        }
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structLayouts.put(struct.name(), StructLayout.from(struct, structNames));
            }
        }
    }

    private TypeId resolveReturnType(String name) {
        if (name == null) {
            return TypeId.VOID;
        }
        TypeId type = resolveTypeName(name);
        if (type == TypeId.UNKNOWN) {
            throw new IllegalStateException("Unknown return type: " + name);
        }
        return type;
    }

    private TypeId resolveTypeName(String name) {
        TypeId base = TypeId.fromTypeName(name);
        if (base != TypeId.UNKNOWN) {
            return base;
        }
        if (structLayouts.containsKey(name)) {
            return TypeId.struct(name);
        }
        return TypeId.UNKNOWN;
    }

    private FunctionInfo functionInfoFrom(AstFunction fn, TypeId returnType) {
        List<ParamInfo> params = new ArrayList<>();
        StringBuilder descriptor = new StringBuilder();
        descriptor.append('(');
        for (AstParam param : fn.params()) {
            TypeId paramType = resolveTypeName(param.type());
            if (paramType == TypeId.UNKNOWN) {
                throw new IllegalStateException("Unknown parameter type: " + param.type());
            }
            if (paramType == TypeId.VOID) {
                throw new IllegalStateException("Parameter type cannot be void");
            }
            ValueKind paramKind = toValueKind(paramType);
            String paramStruct = paramType.isStruct() ? paramType.structName() : null;
            String paramDesc = descriptorFor(paramType);
            params.add(new ParamInfo(param.name(), paramKind, paramStruct, paramDesc));
            descriptor.append(paramDesc);
        }
        descriptor.append(')').append(descriptorFor(returnType));
        ValueKind kind = toValueKind(returnType);
        String structName = returnType.isStruct() ? returnType.structName() : null;
        return new FunctionInfo(fn.name(), kind, structName, descriptor.toString(), params);
    }

    private ValueKind toValueKind(TypeId type) {
        if (type == TypeId.INT) {
            return ValueKind.INT;
        }
        if (type == TypeId.BOOL) {
            return ValueKind.BOOL;
        }
        if (type == TypeId.STRING) {
            return ValueKind.STRING;
        }
        if (type == TypeId.VOID) {
            return ValueKind.VOID;
        }
        if (type.isStruct()) {
            return ValueKind.STRUCT;
        }
        throw new IllegalStateException("Unsupported type: " + type);
    }

    private String descriptorFor(TypeId type) {
        if (type == TypeId.INT) {
            return "I";
        }
        if (type == TypeId.BOOL) {
            return "Z";
        }
        if (type == TypeId.STRING) {
            return "Ljava/lang/String;";
        }
        if (type == TypeId.VOID) {
            return "V";
        }
        if (type.isStruct()) {
            return "L" + type.structName() + ";";
        }
        throw new IllegalStateException("Unsupported type: " + type);
    }

    private enum ValueKind {
        STRING,
        INT,
        BOOL,
        STRUCT,
        VOID
    }

    private record ParamInfo(String name, ValueKind kind, String structName, String descriptor) {}

    private record FunctionInfo(String name, ValueKind returnKind, String returnStructName, String descriptor, List<ParamInfo> params) {
        int paramCount() {
            return params.size();
        }
    }

    private record Local(ValueKind kind, int slot, String structName) {}

    private record ExprValue(ValueKind kind, String structName) {
        static ExprValue of(ValueKind kind) {
            return new ExprValue(kind, null);
        }

        boolean matches(ReturnInfo returnInfo) {
            if (kind != returnInfo.kind()) {
                return false;
            }
            if (kind == ValueKind.STRUCT) {
                return structName != null && structName.equals(returnInfo.structName());
            }
            return true;
        }
    }

    private record ReturnInfo(ValueKind kind, String structName) {}

    private record LoopLabels(Label continueLabel, Label breakLabel) {}

    private static final class LocalState {
        private final Map<String, Local> locals;
        private final AtomicInteger nextSlot;

        private LocalState(int startSlot) {
            this.locals = new HashMap<>();
            this.nextSlot = new AtomicInteger(startSlot);
        }

        private LocalState(Map<String, Local> locals, AtomicInteger nextSlot) {
            this.locals = locals;
            this.nextSlot = nextSlot;
        }

        LocalState fork() {
            return new LocalState(new HashMap<>(locals), nextSlot);
        }

        int allocate(String name, ValueKind kind, String structName) {
            int slot = nextSlot.getAndIncrement();
            locals.put(name, new Local(kind, slot, structName));
            return slot;
        }

        void define(String name, ValueKind kind, String structName, int slot) {
            locals.put(name, new Local(kind, slot, structName));
            nextSlot.updateAndGet(current -> Math.max(current, slot + 1));
        }

        Local get(String name) {
            return locals.get(name);
        }
    }

    private record StructLayout(String name, String internalName, List<FieldInfo> fields) {
        static StructLayout from(AstStruct struct, Set<String> knownStructs) {
            List<FieldInfo> fieldInfos = new ArrayList<>();
            for (AstField field : struct.fields()) {
                fieldInfos.add(FieldInfo.from(field, knownStructs));
            }
            return new StructLayout(struct.name(), struct.name(), fieldInfos);
        }

        String constructorDescriptor() {
            StringBuilder desc = new StringBuilder();
            desc.append('(');
            for (FieldInfo field : fields) {
                desc.append(field.descriptor());
            }
            desc.append(")V");
            return desc.toString();
        }

        FieldInfo field(String name) {
            for (FieldInfo field : fields) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            return null;
        }

        boolean matches(FieldInfo field, ValueKind kind, String structName) {
            if (field.kind() != kind) {
                return false;
            }
            if (kind == ValueKind.STRUCT) {
                return field.structName().equals(structName);
            }
            return true;
        }
    }

    private record FieldInfo(String name, String descriptor, ValueKind kind, String structName) {
        static FieldInfo from(AstField field, Set<String> knownStructs) {
            String type = field.type();
            return switch (type) {
                case "String", "std::String" -> new FieldInfo(field.name(), "Ljava/lang/String;", ValueKind.STRING, null);
                case "i32", "int" -> new FieldInfo(field.name(), "I", ValueKind.INT, null);
                case "bool" -> new FieldInfo(field.name(), "Z", ValueKind.BOOL, null);
                default -> {
                    if (knownStructs.contains(type)) {
                        yield new FieldInfo(field.name(), "L" + type + ";", ValueKind.STRUCT, type);
                    }
                    throw new IllegalStateException("Unsupported field type: " + type);
                }
            };
        }

        String appendDescriptor() {
            return switch (kind) {
                case STRING -> "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                case INT -> "(I)Ljava/lang/StringBuilder;";
                case BOOL -> "(Z)Ljava/lang/StringBuilder;";
                case STRUCT -> "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                default -> "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            };
        }
    }
}
