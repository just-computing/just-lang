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

public final class Codegen implements CodegenStrategy {
    private static final String MAIN_CLASS_NAME = "Main";
    private static final String MAIN_INTERNAL_NAME = "Main";
    private final Map<String, StructLayout> structLayouts = new HashMap<>();
    private final Map<String, EnumLayout> enumLayouts = new HashMap<>();
    private final Map<String, FunctionInfo> functions = new HashMap<>();
    private final Deque<LoopContext> loopStack = new ArrayDeque<>();
    private ReturnInfo currentReturnInfo;

    @Override
    public List<ClassFile> emit(AstModule module) {
        buildEnumLayouts(module);
        buildStructLayouts(module);
        buildFunctionRegistry(module);
        List<ClassFile> classFiles = new ArrayList<>();
        for (StructLayout layout : structLayouts.values()) {
            classFiles.add(emitStructClass(layout));
        }
        for (EnumLayout layout : enumLayouts.values()) {
            classFiles.add(emitEnumClass(layout));
        }
        classFiles.add(emitMainClass(module));
        return classFiles;
    }

    @Override
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

    private ClassFile emitEnumClass(EnumLayout layout) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, layout.internalName(), null, "java/lang/Object", null);

        writer.visitField(Opcodes.ACC_FINAL, "tag", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_FINAL, "payload", "Ljava/lang/Object;", null, null).visitEnd();

        emitEnumConstructor(writer, layout);
        emitEnumFactories(writer, layout);
        emitEnumToString(writer, layout);

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

    private void emitEnumConstructor(ClassWriter writer, EnumLayout layout) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, layout.internalName(), "tag", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, layout.internalName(), "payload", "Ljava/lang/Object;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitEnumFactories(ClassWriter writer, EnumLayout layout) {
        for (EnumVariant variant : layout.variants()) {
            String desc = variant.payloadType() == null
                ? "()" + "L" + layout.internalName() + ";"
                : "(" + descriptorFor(variant.payloadType()) + ")" + "L" + layout.internalName() + ";";
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, variant.name(), desc, null, null);
            mv.visitCode();
            mv.visitTypeInsn(Opcodes.NEW, layout.internalName());
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(variant.tag());
            if (variant.payloadType() == null) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                loadLocal(mv, toValueKind(variant.payloadType()), 0);
                loadAndBoxPayload(mv, variant.payloadType());
            }
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, layout.internalName(), "<init>", "(ILjava/lang/Object;)V", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private void emitEnumToString(ClassWriter writer, EnumLayout layout) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, layout.internalName(), "tag", "I");

        Label defaultLabel = new Label();
        Label endLabel = new Label();
        Label[] labels = new Label[layout.variants().size()];
        int[] keys = new int[layout.variants().size()];
        for (int i = 0; i < layout.variants().size(); i++) {
            labels[i] = new Label();
            keys[i] = layout.variants().get(i).tag();
        }
        mv.visitLookupSwitchInsn(defaultLabel, keys, labels);

        for (int i = 0; i < layout.variants().size(); i++) {
            EnumVariant variant = layout.variants().get(i);
            mv.visitLabel(labels[i]);
            mv.visitLdcInsn(layout.name() + "::" + variant.name());
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        mv.visitLabel(defaultLabel);
        mv.visitLdcInsn(layout.name() + "::?");
        mv.visitLabel(endLabel);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void loadAndBoxPayload(MethodVisitor mv, TypeId payloadType) {
        if (payloadType == TypeId.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            return;
        }
        if (payloadType == TypeId.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            return;
        }
        // String, struct, enum are already Objects on stack
    }

    private void boxIfNeeded(MethodVisitor mv, ValueKind kind) {
        if (kind == ValueKind.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            return;
        }
        if (kind == ValueKind.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }
    }

    private void emitUnboxPayload(MethodVisitor mv, TypeId payloadType) {
        if (payloadType == TypeId.INT) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            return;
        }
        if (payloadType == TypeId.BOOL) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            return;
        }
        if (payloadType == TypeId.STRING) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
            return;
        }
        if (payloadType.isStruct()) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, payloadType.structName());
            return;
        }
        if (payloadType.isEnum()) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, payloadType.enumName());
        }
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
        if (stmt instanceof AstIfLetStmt ifLetStmt) {
            emitIfLet(mv, ifLetStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstForStmt forStmt) {
            emitFor(mv, forStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstLoopStmt loopStmt) {
            emitLoopStmt(mv, loopStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstWhileStmt whileStmt) {
            emitWhile(mv, whileStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstWhileLetStmt whileLetStmt) {
            emitWhileLet(mv, whileLetStmt, locals, returnInfo);
            return;
        }
        if (stmt instanceof AstAssignStmt assignStmt) {
            emitAssign(mv, assignStmt, locals);
            return;
        }
        if (stmt instanceof AstBreakStmt breakStmt) {
            emitBreak(mv, breakStmt, locals);
            return;
        }
        if (stmt instanceof AstContinueStmt continueStmt) {
            emitContinue(mv, continueStmt);
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

    private void emitAssign(MethodVisitor mv, AstAssignStmt assignStmt, LocalState locals) {
        Local local = locals.get(assignStmt.name());
        if (local == null) {
            throw new IllegalStateException("Unknown identifier: " + assignStmt.name());
        }
        String op = assignStmt.operator();
        if ("=".equals(op)) {
            ExprValue value = emitExpr(mv, assignStmt.value(), locals);
            if (value.kind() == ValueKind.VOID) {
                throw new IllegalStateException("Cannot assign void expression to " + assignStmt.name());
            }
            ExprValue coerced = coerceToExpected(mv, value, new ReturnInfo(local.kind(), local.structName()));
            if (coerced == null) {
                throw new IllegalStateException("Type mismatch in assignment to " + assignStmt.name());
            }
            storeLocal(mv, local.kind(), local.slot());
            return;
        }

        if (local.kind() != ValueKind.INT) {
            throw new IllegalStateException("Compound assignment requires int variable");
        }
        loadLocal(mv, ValueKind.INT, local.slot());
        ExprValue value = emitExpr(mv, assignStmt.value(), locals);
        if (value.kind() != ValueKind.INT) {
            throw new IllegalStateException("Compound assignment requires int value");
        }
        switch (op) {
            case "+=" -> mv.visitInsn(Opcodes.IADD);
            case "-=" -> mv.visitInsn(Opcodes.ISUB);
            case "*=" -> mv.visitInsn(Opcodes.IMUL);
            case "/=" -> mv.visitInsn(Opcodes.IDIV);
            default -> throw new IllegalStateException("Unsupported assignment operator: " + op);
        }
        storeLocal(mv, ValueKind.INT, local.slot());
    }

    private void emitIf(MethodVisitor mv, AstIfStmt ifStmt, LocalState locals, ReturnInfo returnInfo) {
        ExprValue condition = emitExpr(mv, ifStmt.condition(), locals);
        ExprValue coerced = coerceToExpected(mv, condition, new ReturnInfo(ValueKind.BOOL, null));
        if (coerced == null) {
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

    private void emitIfLet(MethodVisitor mv, AstIfLetStmt ifLetStmt, LocalState locals, ReturnInfo returnInfo) {
        ExprValue target = emitExpr(mv, ifLetStmt.target(), locals);
        int targetSlot = locals.allocateTemp();
        storeLocal(mv, target.kind(), targetSlot);

        Label thenLabel = new Label();
        Label elseLabel = new Label();
        Label endLabel = new Label();

        if (ifLetStmt.pattern().kind() == AstMatchPattern.Kind.WILDCARD) {
            mv.visitJumpInsn(Opcodes.GOTO, thenLabel);
        } else {
            emitMatchCompare(mv, target, targetSlot, ifLetStmt.pattern(), thenLabel);
            mv.visitJumpInsn(Opcodes.GOTO, elseLabel);
        }

        mv.visitLabel(thenLabel);
        LocalState thenLocals = locals.fork();
        bindPattern(mv, ifLetStmt.pattern(), targetSlot, thenLocals);
        emitBlock(mv, ifLetStmt.thenBranch(), thenLocals, returnInfo);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(elseLabel);
        if (ifLetStmt.elseBranch() != null) {
            emitBlock(mv, ifLetStmt.elseBranch(), locals.fork(), returnInfo);
        }
        mv.visitLabel(endLabel);
    }

    private void emitWhileLet(MethodVisitor mv, AstWhileLetStmt whileLetStmt, LocalState locals, ReturnInfo returnInfo) {
        Label startLabel = new Label();
        Label continueLabel = new Label();
        Label endLabel = new Label();

        mv.visitLabel(startLabel);
        ExprValue target = emitExpr(mv, whileLetStmt.target(), locals);
        int targetSlot = locals.allocateTemp();
        storeLocal(mv, target.kind(), targetSlot);

        if (whileLetStmt.pattern().kind() == AstMatchPattern.Kind.WILDCARD) {
            // always match
        } else {
            emitMatchCompare(mv, target, targetSlot, whileLetStmt.pattern(), continueLabel);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }

        mv.visitLabel(continueLabel);
        LocalState bodyLocals = locals.fork();
        bindPattern(mv, whileLetStmt.pattern(), targetSlot, bodyLocals);
        loopStack.push(new LoopContext(whileLetStmt.label(), startLabel, endLabel, false, -1));
        emitBlock(mv, whileLetStmt.body(), bodyLocals, returnInfo);
        loopStack.pop();
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitWhile(MethodVisitor mv, AstWhileStmt whileStmt, LocalState locals, ReturnInfo returnInfo) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        mv.visitLabel(startLabel);
        ExprValue condition = emitExpr(mv, whileStmt.condition(), locals);
        ExprValue coerced = coerceToExpected(mv, condition, new ReturnInfo(ValueKind.BOOL, null));
        if (coerced == null) {
            throw new IllegalStateException("while condition must be bool");
        }
        mv.visitJumpInsn(Opcodes.IFEQ, endLabel);
        loopStack.push(new LoopContext(whileStmt.label(), startLabel, endLabel, false, -1));
        emitBlock(mv, whileStmt.body(), locals.fork(), returnInfo);
        loopStack.pop();
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitFor(MethodVisitor mv, AstForStmt forStmt, LocalState locals, ReturnInfo returnInfo) {
        LocalState loopLocals = locals.fork();
        int loopSlot = loopLocals.allocate(forStmt.name(), ValueKind.INT, null);
        ExprValue startValue = emitExpr(mv, forStmt.start(), locals);
        if (startValue.kind() != ValueKind.INT) {
            throw new IllegalStateException("for loop start must be int");
        }
        storeLocal(mv, ValueKind.INT, loopSlot);

        ExprValue endValue = emitExpr(mv, forStmt.end(), locals);
        if (endValue.kind() != ValueKind.INT) {
            throw new IllegalStateException("for loop end must be int");
        }
        int endSlot = loopLocals.allocateTemp();
        storeLocal(mv, ValueKind.INT, endSlot);

        Label startLabel = new Label();
        Label continueLabel = new Label();
        Label endLabel = new Label();

        mv.visitLabel(startLabel);
        loadLocal(mv, ValueKind.INT, loopSlot);
        loadLocal(mv, ValueKind.INT, endSlot);
        int jumpOp = forStmt.inclusive() ? Opcodes.IF_ICMPGT : Opcodes.IF_ICMPGE;
        mv.visitJumpInsn(jumpOp, endLabel);

        loopStack.push(new LoopContext(forStmt.label(), continueLabel, endLabel, false, -1));
        emitBlock(mv, forStmt.body(), loopLocals.fork(), returnInfo);
        loopStack.pop();

        mv.visitLabel(continueLabel);
        loadLocal(mv, ValueKind.INT, loopSlot);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        storeLocal(mv, ValueKind.INT, loopSlot);
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitLoopStmt(MethodVisitor mv, AstLoopStmt loopStmt, LocalState locals, ReturnInfo returnInfo) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        mv.visitLabel(startLabel);
        loopStack.push(new LoopContext(loopStmt.label(), startLabel, endLabel, false, -1));
        emitBlock(mv, loopStmt.body(), locals.fork(), returnInfo);
        loopStack.pop();
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private ExprValue emitLoopExpr(MethodVisitor mv, AstLoopExpr loopExpr, LocalState locals) {
        if (currentReturnInfo == null) {
            throw new IllegalStateException("Missing return context for loop expression");
        }
        Label startLabel = new Label();
        Label continueLabel = new Label();
        Label endLabel = new Label();
        int resultSlot = locals.allocateTemp();

        LoopContext context = new LoopContext(null, continueLabel, endLabel, true, resultSlot);
        loopStack.push(context);

        mv.visitLabel(startLabel);
        emitBlock(mv, loopExpr.body(), locals.fork(), currentReturnInfo);
        mv.visitLabel(continueLabel);
        mv.visitJumpInsn(Opcodes.GOTO, startLabel);
        mv.visitLabel(endLabel);

        loopStack.pop();

        if (context.resultKind == null) {
            throw new IllegalStateException("loop expression requires break with value");
        }
        loadLocal(mv, context.resultKind, resultSlot);
        return new ExprValue(context.resultKind, context.resultStructName);
    }

    private void emitBreak(MethodVisitor mv, AstBreakStmt breakStmt, LocalState locals) {
        LoopContext context = resolveLoopContext(breakStmt.label(), "break");
        if (context == null) {
            throw new IllegalStateException("break is only valid inside loops");
        }
        if (breakStmt.expr() != null) {
            if (!context.valueLoop) {
                throw new IllegalStateException("break with value is only allowed in loop expressions");
            }
            ExprValue value = emitExpr(mv, breakStmt.expr(), locals);
            if (value.kind() == ValueKind.VOID) {
                throw new IllegalStateException("break value cannot be void");
            }
            if (context.resultKind == null) {
                context.resultKind = value.kind();
                context.resultStructName = value.structName();
            } else if (!value.matches(new ReturnInfo(context.resultKind, context.resultStructName))) {
                throw new IllegalStateException("break values must match in loop expression");
            }
            storeLocal(mv, value.kind(), context.resultSlot);
        } else if (context.valueLoop) {
            throw new IllegalStateException("break value required for loop expression");
        }
        mv.visitJumpInsn(Opcodes.GOTO, context.breakLabel);
    }

    private void emitContinue(MethodVisitor mv, AstContinueStmt continueStmt) {
        LoopContext context = resolveLoopContext(continueStmt.label(), "continue");
        if (context == null) {
            throw new IllegalStateException("continue is only valid inside loops");
        }
        mv.visitJumpInsn(Opcodes.GOTO, context.continueLabel);
    }

    private LoopContext resolveLoopContext(String label, String keyword) {
        if (loopStack.isEmpty()) {
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
        throw new IllegalStateException("Unknown loop label '" + label + "' for " + keyword);
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
        ExprValue coerced = coerceToExpected(mv, value, returnInfo);
        if (coerced == null) {
            throw new IllegalStateException("Return type mismatch");
        }
        mv.visitInsn(switch (returnInfo.kind()) {
            case INT, BOOL -> Opcodes.IRETURN;
            case STRING, STRUCT, ENUM, ANY -> Opcodes.ARETURN;
            case VOID -> Opcodes.RETURN;
        });
    }

    private ExprValue emitIfExpr(MethodVisitor mv, AstIfExpr ifExpr, LocalState locals) {
        ExprValue condition = emitExpr(mv, ifExpr.condition(), locals);
        ExprValue coerced = coerceToExpected(mv, condition, new ReturnInfo(ValueKind.BOOL, null));
        if (coerced == null) {
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

    private ExprValue emitMatchExpr(MethodVisitor mv, AstMatchExpr matchExpr, LocalState locals) {
        ExprValue target = emitExpr(mv, matchExpr.target(), locals);
        if (target.kind() != ValueKind.INT && target.kind() != ValueKind.BOOL && target.kind() != ValueKind.STRING && target.kind() != ValueKind.ENUM) {
            throw new IllegalStateException("match target must be int, bool, String, or enum");
        }
        int targetSlot = locals.allocateTemp();
        int resultSlot = locals.allocateTemp();
        storeLocal(mv, target.kind(), targetSlot);

        Label endLabel = new Label();
        Label noMatchLabel = new Label();
        ExprValue resultType = ExprValue.of(ValueKind.ANY);
        boolean hasWildcard = false;
        List<AstMatchArm> arms = matchExpr.arms();
        List<Label> checkLabels = new ArrayList<>();
        for (int i = 0; i <= arms.size(); i++) {
            checkLabels.add(new Label());
        }

        mv.visitLabel(checkLabels.get(0));
        for (int i = 0; i < arms.size(); i++) {
            AstMatchArm arm = arms.get(i);
            AstMatchPattern pattern = arm.pattern();
            Label nextCheck = checkLabels.get(i + 1);

            if (pattern.kind() != AstMatchPattern.Kind.WILDCARD) {
                Label patternMatch = new Label();
                emitMatchCompare(mv, target, targetSlot, pattern, patternMatch);
                mv.visitJumpInsn(Opcodes.GOTO, nextCheck);
                mv.visitLabel(patternMatch);
            } else if (arm.guard() == null) {
                hasWildcard = true;
            }

            LocalState armLocals = locals.fork();
            bindPattern(mv, pattern, targetSlot, armLocals);
            if (arm.guard() != null) {
                ExprValue guard = emitExpr(mv, arm.guard(), armLocals);
                if (guard.kind() != ValueKind.BOOL) {
                    throw new IllegalStateException("match guard must be bool");
                }
                mv.visitJumpInsn(Opcodes.IFEQ, nextCheck);
            }

            ExprValue value = emitExpr(mv, arm.expr(), armLocals);
            if (value.kind() == ValueKind.VOID) {
                throw new IllegalStateException("match arm cannot be void");
            }
            ExprValue coerced = coerceToExpected(mv, value, new ReturnInfo(ValueKind.ANY, null));
            if (coerced == null) {
                throw new IllegalStateException("match arms must return a value");
            }
            storeLocal(mv, ValueKind.ANY, resultSlot);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(nextCheck);
        }

        mv.visitJumpInsn(Opcodes.GOTO, noMatchLabel);

        if (!hasWildcard) {
            mv.visitLabel(noMatchLabel);
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Non-exhaustive match");
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
        } else {
            mv.visitLabel(noMatchLabel);
        }

        mv.visitLabel(endLabel);
        loadLocal(mv, ValueKind.ANY, resultSlot);
        return resultType;
    }

    private void emitMatchCompare(MethodVisitor mv, ExprValue target, int targetSlot, AstMatchPattern pattern, Label matchLabel) {
        switch (pattern.kind()) {
            case INT -> {
                if (target.kind() != ValueKind.INT) {
                    throw new IllegalStateException("match int pattern used on non-int target");
                }
                loadLocal(mv, ValueKind.INT, targetSlot);
                mv.visitLdcInsn(Integer.parseInt(pattern.value()));
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, matchLabel);
            }
            case BOOL -> {
                if (target.kind() != ValueKind.BOOL) {
                    throw new IllegalStateException("match bool pattern used on non-bool target");
                }
                loadLocal(mv, ValueKind.BOOL, targetSlot);
                boolean boolValue = Boolean.parseBoolean(pattern.value());
                mv.visitInsn(boolValue ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, matchLabel);
            }
            case STRING -> {
                if (target.kind() != ValueKind.STRING) {
                    throw new IllegalStateException("match string pattern used on non-string target");
                }
                loadLocal(mv, ValueKind.STRING, targetSlot);
                mv.visitLdcInsn(pattern.value());
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFNE, matchLabel);
            }
            case RANGE -> {
                if (target.kind() != ValueKind.INT) {
                    throw new IllegalStateException("match range pattern used on non-int target");
                }
                int start = Integer.parseInt(pattern.rangeStart());
                int end = Integer.parseInt(pattern.rangeEnd());
                Label fail = new Label();
                loadLocal(mv, ValueKind.INT, targetSlot);
                mv.visitLdcInsn(start);
                mv.visitJumpInsn(Opcodes.IF_ICMPLT, fail);
                loadLocal(mv, ValueKind.INT, targetSlot);
                mv.visitLdcInsn(end);
                int jumpOp = pattern.inclusive() ? Opcodes.IF_ICMPLE : Opcodes.IF_ICMPLT;
                mv.visitJumpInsn(jumpOp, matchLabel);
                mv.visitLabel(fail);
            }
            case ENUM -> {
                if (target.kind() != ValueKind.ENUM) {
                    throw new IllegalStateException("match enum pattern used on non-enum target");
                }
                EnumLayout layout = enumLayouts.get(pattern.enumName());
                if (layout == null) {
                    throw new IllegalStateException("Unknown enum: " + pattern.enumName());
                }
                EnumVariant variant = layout.variant(pattern.variantName());
                if (variant == null) {
                    throw new IllegalStateException("Unknown variant: " + pattern.variantName());
                }
                loadLocal(mv, ValueKind.ENUM, targetSlot);
                mv.visitFieldInsn(Opcodes.GETFIELD, layout.internalName(), "tag", "I");
                mv.visitLdcInsn(variant.tag());
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, matchLabel);
            }
            case WILDCARD -> {
                // handled elsewhere
            }
        }
    }

    private void bindPattern(MethodVisitor mv, AstMatchPattern pattern, int targetSlot, LocalState locals) {
        if (pattern.kind() != AstMatchPattern.Kind.ENUM) {
            return;
        }
        if (pattern.binding() == null) {
            return;
        }
        EnumLayout layout = enumLayouts.get(pattern.enumName());
        if (layout == null) {
            throw new IllegalStateException("Unknown enum: " + pattern.enumName());
        }
        EnumVariant variant = layout.variant(pattern.variantName());
        if (variant == null) {
            throw new IllegalStateException("Unknown variant: " + pattern.variantName());
        }
        if (variant.payloadType() == null) {
            throw new IllegalStateException("Variant '" + variant.name() + "' does not bind a value");
        }
        String typeName = variant.payloadType().isStruct()
            ? variant.payloadType().structName()
            : variant.payloadType().isEnum() ? variant.payloadType().enumName() : null;
        int slot = locals.allocate(pattern.binding(), toValueKind(variant.payloadType()), typeName);
        loadLocal(mv, ValueKind.ENUM, targetSlot);
        mv.visitFieldInsn(Opcodes.GETFIELD, layout.internalName(), "payload", "Ljava/lang/Object;");
        emitUnboxPayload(mv, variant.payloadType());
        storeLocal(mv, toValueKind(variant.payloadType()), slot);
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
        if (expr instanceof AstMatchExpr matchExpr) {
            return emitMatchExpr(mv, matchExpr, locals);
        }
        if (expr instanceof AstLoopExpr loopExpr) {
            return emitLoopExpr(mv, loopExpr, locals);
        }
        if (expr instanceof AstPathExpr pathExpr) {
            return emitEnumPath(mv, pathExpr, locals);
        }
        throw new IllegalStateException("Unsupported expression: " + expr.getClass().getSimpleName());
    }

    private ExprValue emitCall(MethodVisitor mv, AstCallExpr call, LocalState locals) {
        if (!isPrintCall(call)) {
            if (call.callee().size() == 2) {
                return emitEnumCall(mv, call, locals);
            }
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
                ExprValue coerced = coerceToExpected(mv, arg, new ReturnInfo(param.kind(), param.structName()));
                if (coerced == null) {
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
        } else if (arg.kind() == ValueKind.STRUCT || arg.kind() == ValueKind.ENUM || arg.kind() == ValueKind.ANY) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
        } else {
            throw new IllegalStateException("Unsupported print argument type: " + arg.kind());
        }
        return ExprValue.of(ValueKind.VOID);
    }

    private ExprValue emitEnumPath(MethodVisitor mv, AstPathExpr pathExpr, LocalState locals) {
        List<String> segments = pathExpr.segments();
        if (segments.size() != 2) {
            throw new IllegalStateException("Unsupported path expression: " + String.join("::", segments));
        }
        String enumName = segments.get(0);
        String variantName = segments.get(1);
        EnumLayout layout = enumLayouts.get(enumName);
        if (layout == null) {
            throw new IllegalStateException("Unknown enum: " + enumName);
        }
        EnumVariant variant = layout.variant(variantName);
        if (variant == null) {
            throw new IllegalStateException("Unknown variant: " + variantName);
        }
        if (variant.payloadType() != null) {
            throw new IllegalStateException("Variant '" + variantName + "' requires a value");
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, layout.internalName(), variantName, "()" + "L" + layout.internalName() + ";", false);
        return new ExprValue(ValueKind.ENUM, layout.name());
    }

    private ExprValue emitEnumCall(MethodVisitor mv, AstCallExpr call, LocalState locals) {
        String enumName = call.callee().get(0);
        String variantName = call.callee().get(1);
        EnumLayout layout = enumLayouts.get(enumName);
        if (layout == null) {
            throw new IllegalStateException("Unknown enum: " + enumName);
        }
        EnumVariant variant = layout.variant(variantName);
        if (variant == null) {
            throw new IllegalStateException("Unknown variant: " + variantName);
        }
        if (variant.payloadType() == null) {
            if (!call.args().isEmpty()) {
                throw new IllegalStateException("Variant '" + variantName + "' does not take a value");
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, layout.internalName(), variantName, "()" + "L" + layout.internalName() + ";", false);
            return new ExprValue(ValueKind.ENUM, layout.name());
        }
        if (call.args().size() != 1) {
            throw new IllegalStateException("Variant '" + variantName + "' expects one argument");
        }
        ExprValue arg = emitExpr(mv, call.args().get(0), locals);
        if (!arg.matches(new ReturnInfo(toValueKind(variant.payloadType()), variant.payloadType().isStruct() ? variant.payloadType().structName() : variant.payloadType().enumName()))) {
            throw new IllegalStateException("Variant '" + variantName + "' argument type mismatch");
        }
        if (variant.payloadType() == TypeId.ANY) {
            boxIfNeeded(mv, arg.kind());
        }
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            layout.internalName(),
            variantName,
            "(" + descriptorFor(variant.payloadType()) + ")" + "L" + layout.internalName() + ";",
            false
        );
        return new ExprValue(ValueKind.ENUM, layout.name());
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
        if ("&".equals(unaryExpr.operator()) || "&mut".equals(unaryExpr.operator()) || "*".equals(unaryExpr.operator())) {
            // `&`, `&mut`, and `*` only affect compile-time borrow typing in this backend.
            // At runtime we keep the same JVM value and emit no additional instructions.
            return emitExpr(mv, unaryExpr.expr(), locals);
        }
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
            if (!coerceBinaryIntOperands(mv, left, right)) {
                throw new IllegalStateException("Arithmetic requires int operands");
            }
            mv.visitInsn(Opcodes.IADD);
            return ExprValue.of(ValueKind.INT);
        }
        if ("-".equals(op)) {
            if (!coerceBinaryIntOperands(mv, left, right)) {
                throw new IllegalStateException("Arithmetic requires int operands");
            }
            mv.visitInsn(Opcodes.ISUB);
            return ExprValue.of(ValueKind.INT);
        }
        if ("*".equals(op)) {
            if (!coerceBinaryIntOperands(mv, left, right)) {
                throw new IllegalStateException("Arithmetic requires int operands");
            }
            mv.visitInsn(Opcodes.IMUL);
            return ExprValue.of(ValueKind.INT);
        }
        if ("/".equals(op)) {
            if (!coerceBinaryIntOperands(mv, left, right)) {
                throw new IllegalStateException("Arithmetic requires int operands");
            }
            mv.visitInsn(Opcodes.IDIV);
            return ExprValue.of(ValueKind.INT);
        }
        if ("==".equals(op) || "!=".equals(op)) {
            if (left.kind() == ValueKind.ANY || right.kind() == ValueKind.ANY) {
                coerceBothToObjectsForEquality(mv, left, right);
                return emitAnyEquality(mv, op);
            }
            return emitEquality(mv, op, left);
        }
        if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
            if (!coerceBinaryIntOperands(mv, left, right)) {
                throw new IllegalStateException("Comparison requires int operands");
            }
            return emitComparison(mv, op);
        }
        throw new IllegalStateException("Unsupported binary operator: " + op);
    }

    private ExprValue emitLogicalAnd(MethodVisitor mv, AstExpr leftExpr, AstExpr rightExpr, LocalState locals) {
        Label falseLabel = new Label();
        Label endLabel = new Label();
        ExprValue left = emitExpr(mv, leftExpr, locals);
        ExprValue leftCoerced = coerceToExpected(mv, left, new ReturnInfo(ValueKind.BOOL, null));
        if (leftCoerced == null) {
            throw new IllegalStateException("Logical && requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        ExprValue right = emitExpr(mv, rightExpr, locals);
        ExprValue rightCoerced = coerceToExpected(mv, right, new ReturnInfo(ValueKind.BOOL, null));
        if (rightCoerced == null) {
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
        ExprValue leftCoerced = coerceToExpected(mv, left, new ReturnInfo(ValueKind.BOOL, null));
        if (leftCoerced == null) {
            throw new IllegalStateException("Logical || requires bool operands");
        }
        mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        ExprValue right = emitExpr(mv, rightExpr, locals);
        ExprValue rightCoerced = coerceToExpected(mv, right, new ReturnInfo(ValueKind.BOOL, null));
        if (rightCoerced == null) {
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
        if (left.kind() == ValueKind.ANY) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            if (negate) {
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IXOR);
            }
            return ExprValue.of(ValueKind.BOOL);
        }
        int opcode;
        if (left.kind() == ValueKind.STRUCT || left.kind() == ValueKind.ENUM) {
            opcode = negate ? Opcodes.IF_ACMPNE : Opcodes.IF_ACMPEQ;
        } else {
            opcode = negate ? Opcodes.IF_ICMPNE : Opcodes.IF_ICMPEQ;
        }
        return emitBooleanJump(mv, opcode);
    }

    private ExprValue emitAnyEquality(MethodVisitor mv, String op) {
        boolean negate = "!=".equals(op);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
        if (negate) {
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IXOR);
        }
        return ExprValue.of(ValueKind.BOOL);
    }

    private boolean coerceBinaryIntOperands(MethodVisitor mv, ExprValue left, ExprValue right) {
        if (!isIntLike(left.kind()) || !isIntLike(right.kind())) {
            return false;
        }
        if (right.kind() == ValueKind.ANY) {
            unboxAnyTopToInt(mv);
        }
        if (left.kind() == ValueKind.ANY) {
            mv.visitInsn(Opcodes.SWAP);
            unboxAnyTopToInt(mv);
            mv.visitInsn(Opcodes.SWAP);
        }
        return true;
    }

    private void coerceBothToObjectsForEquality(MethodVisitor mv, ExprValue left, ExprValue right) {
        if (right.kind() == ValueKind.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (right.kind() == ValueKind.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }

        if (left.kind() == ValueKind.INT) {
            mv.visitInsn(Opcodes.SWAP);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            mv.visitInsn(Opcodes.SWAP);
        } else if (left.kind() == ValueKind.BOOL) {
            mv.visitInsn(Opcodes.SWAP);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            mv.visitInsn(Opcodes.SWAP);
        }
    }

    private ExprValue coerceToExpected(MethodVisitor mv, ExprValue value, ReturnInfo expected) {
        if (expected.kind() == ValueKind.ANY) {
            if (value.kind() == ValueKind.INT) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                return ExprValue.of(ValueKind.ANY);
            }
            if (value.kind() == ValueKind.BOOL) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                return ExprValue.of(ValueKind.ANY);
            }
            if (value.kind() == ValueKind.STRING || value.kind() == ValueKind.STRUCT || value.kind() == ValueKind.ENUM || value.kind() == ValueKind.ANY) {
                return ExprValue.of(ValueKind.ANY);
            }
            return null;
        }

        if (value.matches(expected)) {
            return value;
        }

        if (value.kind() == ValueKind.ANY) {
            if (expected.kind() == ValueKind.INT) {
                unboxAnyTopToInt(mv);
                return ExprValue.of(ValueKind.INT);
            }
            if (expected.kind() == ValueKind.BOOL) {
                unboxAnyTopToBool(mv);
                return ExprValue.of(ValueKind.BOOL);
            }
            if (expected.kind() == ValueKind.STRING) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                return ExprValue.of(ValueKind.STRING);
            }
            if (expected.kind() == ValueKind.STRUCT || expected.kind() == ValueKind.ENUM) {
                if (expected.structName() == null) {
                    return null;
                }
                mv.visitTypeInsn(Opcodes.CHECKCAST, expected.structName());
                return new ExprValue(expected.kind(), expected.structName());
            }
            if (expected.kind() == ValueKind.ANY) {
                return ExprValue.of(ValueKind.ANY);
            }
            return null;
        }

        return null;
    }

    private boolean isIntLike(ValueKind kind) {
        return kind == ValueKind.INT || kind == ValueKind.ANY;
    }

    private void unboxAnyTopToInt(MethodVisitor mv) {
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
    }

    private void unboxAnyTopToBool(MethodVisitor mv) {
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
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
        if (kind == ValueKind.STRING || kind == ValueKind.STRUCT || kind == ValueKind.ENUM || kind == ValueKind.ANY) {
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
        if (kind == ValueKind.STRING || kind == ValueKind.STRUCT || kind == ValueKind.ENUM || kind == ValueKind.ANY) {
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

    private void buildEnumLayouts(AstModule module) {
        Set<String> structNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();
        for (AstEnum builtin : builtinEnums()) {
            enumNames.add(builtin.name());
        }
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structNames.add(struct.name());
            }
            if (item instanceof AstEnum enumDef) {
                if (enumNames.contains(enumDef.name())) {
                    throw new IllegalStateException("Enum name is reserved or already defined: " + enumDef.name());
                }
                enumNames.add(enumDef.name());
            }
        }
        for (AstEnum builtin : builtinEnums()) {
            addEnumLayout(builtin, structNames, enumNames);
        }
        for (AstItem item : module.items()) {
            if (item instanceof AstEnum enumDef) {
                addEnumLayout(enumDef, structNames, enumNames);
            }
        }
    }

    private void buildStructLayouts(AstModule module) {
        Set<String> structNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();
        for (AstEnum builtin : builtinEnums()) {
            enumNames.add(builtin.name());
        }
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structNames.add(struct.name());
            }
            if (item instanceof AstEnum enumDef) {
                enumNames.add(enumDef.name());
            }
        }
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structLayouts.put(struct.name(), StructLayout.from(struct, structNames, enumNames));
            }
        }
    }

    private void addEnumLayout(AstEnum enumDef, Set<String> structNames, Set<String> enumNames) {
        List<EnumVariant> variants = new ArrayList<>();
        int tag = 0;
        for (AstEnumVariant variant : enumDef.variants()) {
            TypeId payloadType = null;
            if (variant.payloadType() != null) {
                payloadType = resolveTypeName(variant.payloadType(), structNames, enumNames);
                if (payloadType == TypeId.UNKNOWN || payloadType == TypeId.VOID) {
                    throw new IllegalStateException("Unsupported payload type: " + variant.payloadType());
                }
            }
            variants.add(new EnumVariant(variant.name(), tag++, payloadType));
        }
        enumLayouts.put(enumDef.name(), new EnumLayout(enumDef.name(), enumDef.name(), variants));
    }

    private List<AstEnum> builtinEnums() {
        List<AstEnumVariant> optionVariants = List.of(
            new AstEnumVariant("Some", "Any"),
            new AstEnumVariant("None", null)
        );
        List<AstEnumVariant> resultVariants = List.of(
            new AstEnumVariant("Ok", "Any"),
            new AstEnumVariant("Err", "Any")
        );
        return List.of(
            new AstEnum("Option", optionVariants),
            new AstEnum("Result", resultVariants)
        );
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
        TypeId reference = parseReferenceType(name);
        if (reference != null) {
            return reference;
        }
        TypeId generic = parseGenericType(name);
        if (generic != null) {
            return generic;
        }
        TypeId base = TypeId.fromTypeName(name);
        if (base != TypeId.UNKNOWN) {
            return base;
        }
        if (structLayouts.containsKey(name)) {
            return TypeId.struct(name);
        }
        if (enumLayouts.containsKey(name)) {
            return TypeId.enumType(name);
        }
        return TypeId.UNKNOWN;
    }

    private TypeId resolveTypeName(String name, Set<String> knownStructs, Set<String> knownEnums) {
        TypeId reference = parseReferenceType(name, knownStructs, knownEnums);
        if (reference != null) {
            return reference;
        }
        TypeId generic = parseGenericType(name, knownStructs, knownEnums);
        if (generic != null) {
            return generic;
        }
        TypeId base = TypeId.fromTypeName(name);
        if (base != TypeId.UNKNOWN) {
            return base;
        }
        if (knownStructs.contains(name)) {
            return TypeId.struct(name);
        }
        if (knownEnums.contains(name)) {
            return TypeId.enumType(name);
        }
        return TypeId.UNKNOWN;
    }

    private TypeId parseReferenceType(String name) {
        ParsedReferenceType parsedReference = parseReferenceSyntax(name);
        if (parsedReference == null) {
            return null;
        }
        if (parsedReference.innerTypeText().isEmpty()) {
            return TypeId.UNKNOWN;
        }
        TypeId innerType = resolveTypeName(parsedReference.innerTypeText());
        if (innerType == TypeId.UNKNOWN || innerType == TypeId.VOID) {
            return TypeId.UNKNOWN;
        }
        return TypeId.reference(innerType, parsedReference.mutable());
    }

    private TypeId parseReferenceType(String name, Set<String> knownStructs, Set<String> knownEnums) {
        ParsedReferenceType parsedReference = parseReferenceSyntax(name);
        if (parsedReference == null) {
            return null;
        }
        if (parsedReference.innerTypeText().isEmpty()) {
            return TypeId.UNKNOWN;
        }
        TypeId innerType = resolveTypeName(parsedReference.innerTypeText(), knownStructs, knownEnums);
        if (innerType == TypeId.UNKNOWN || innerType == TypeId.VOID) {
            return TypeId.UNKNOWN;
        }
        return TypeId.reference(innerType, parsedReference.mutable());
    }

    private ParsedReferenceType parseReferenceSyntax(String typeName) {
        String trimmed = typeName.trim();
        if (!trimmed.startsWith("&")) {
            return null;
        }
        if (trimmed.startsWith("&mut")) {
            return new ParsedReferenceType(true, trimmed.substring("&mut".length()).trim());
        }
        return new ParsedReferenceType(false, trimmed.substring(1).trim());
    }

    private TypeId parseGenericType(String name) {
        String trimmed = name.trim();
        if (trimmed.startsWith("Option<") && trimmed.endsWith(">")) {
            String inner = trimmed.substring("Option<".length(), trimmed.length() - 1).trim();
            TypeId innerType = resolveTypeName(inner);
            return innerType == TypeId.UNKNOWN ? TypeId.UNKNOWN : TypeId.option(innerType);
        }
        if (trimmed.startsWith("Result<") && trimmed.endsWith(">")) {
            String inside = trimmed.substring("Result<".length(), trimmed.length() - 1).trim();
            int split = findTopLevelComma(inside);
            if (split < 0) {
                return TypeId.UNKNOWN;
            }
            TypeId ok = resolveTypeName(inside.substring(0, split).trim());
            TypeId err = resolveTypeName(inside.substring(split + 1).trim());
            if (ok == TypeId.UNKNOWN || err == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            return TypeId.result(ok, err);
        }
        return null;
    }

    private TypeId parseGenericType(String name, Set<String> knownStructs, Set<String> knownEnums) {
        String trimmed = name.trim();
        if (trimmed.startsWith("Option<") && trimmed.endsWith(">")) {
            String inner = trimmed.substring("Option<".length(), trimmed.length() - 1).trim();
            TypeId innerType = resolveTypeName(inner, knownStructs, knownEnums);
            return innerType == TypeId.UNKNOWN ? TypeId.UNKNOWN : TypeId.option(innerType);
        }
        if (trimmed.startsWith("Result<") && trimmed.endsWith(">")) {
            String inside = trimmed.substring("Result<".length(), trimmed.length() - 1).trim();
            int split = findTopLevelComma(inside);
            if (split < 0) {
                return TypeId.UNKNOWN;
            }
            TypeId ok = resolveTypeName(inside.substring(0, split).trim(), knownStructs, knownEnums);
            TypeId err = resolveTypeName(inside.substring(split + 1).trim(), knownStructs, knownEnums);
            if (ok == TypeId.UNKNOWN || err == TypeId.UNKNOWN) {
                return TypeId.UNKNOWN;
            }
            return TypeId.result(ok, err);
        }
        return null;
    }

    private int findTopLevelComma(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
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
            TypeId erasedParamType = runtimeType(paramType);
            String paramStruct = erasedParamType.isStruct() ? erasedParamType.structName() : erasedParamType.isEnum() ? erasedParamType.enumName() : null;
            String paramDesc = descriptorFor(paramType);
            params.add(new ParamInfo(param.name(), paramKind, paramStruct, paramDesc));
            descriptor.append(paramDesc);
        }
        descriptor.append(')').append(descriptorFor(returnType));
        ValueKind kind = toValueKind(returnType);
        TypeId erasedReturnType = runtimeType(returnType);
        String structName = erasedReturnType.isStruct() ? erasedReturnType.structName() : erasedReturnType.isEnum() ? erasedReturnType.enumName() : null;
        return new FunctionInfo(fn.name(), kind, structName, descriptor.toString(), params);
    }

    private ValueKind toValueKind(TypeId type) {
        if (type.isReference()) {
            return toValueKind(type.referenceInner());
        }
        if (type == TypeId.INT) {
            return ValueKind.INT;
        }
        if (type == TypeId.BOOL) {
            return ValueKind.BOOL;
        }
        if (type == TypeId.STRING) {
            return ValueKind.STRING;
        }
        if (type == TypeId.ANY) {
            return ValueKind.ANY;
        }
        if (type == TypeId.VOID) {
            return ValueKind.VOID;
        }
        if (type.isStruct()) {
            return ValueKind.STRUCT;
        }
        if (type.isEnum()) {
            return ValueKind.ENUM;
        }
        throw new IllegalStateException("Unsupported type: " + type);
    }

    private String descriptorFor(TypeId type) {
        if (type.isReference()) {
            return descriptorFor(type.referenceInner());
        }
        if (type == TypeId.INT) {
            return "I";
        }
        if (type == TypeId.BOOL) {
            return "Z";
        }
        if (type == TypeId.STRING) {
            return "Ljava/lang/String;";
        }
        if (type == TypeId.ANY) {
            return "Ljava/lang/Object;";
        }
        if (type == TypeId.VOID) {
            return "V";
        }
        if (type.isStruct()) {
            return "L" + type.structName() + ";";
        }
        if (type.isEnum()) {
            return "L" + type.enumName() + ";";
        }
        throw new IllegalStateException("Unsupported type: " + type);
    }

    private TypeId runtimeType(TypeId type) {
        TypeId current = type;
        while (current.isReference()) {
            current = current.referenceInner();
        }
        return current;
    }

    private record ParsedReferenceType(boolean mutable, String innerTypeText) {}

    private enum ValueKind {
        STRING,
        INT,
        BOOL,
        ANY,
        ENUM,
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
            if (returnInfo.kind() == ValueKind.ANY) {
                return kind != ValueKind.VOID;
            }
            if (kind != returnInfo.kind()) {
                return false;
            }
            if (kind == ValueKind.STRUCT || kind == ValueKind.ENUM) {
                return structName != null && structName.equals(returnInfo.structName());
            }
            return true;
        }
    }

    private record ReturnInfo(ValueKind kind, String structName) {}

    private record ArmCode(AstMatchArm arm, Label label) {}

    private static final class LoopContext {
        private final String label;
        private final Label continueLabel;
        private final Label breakLabel;
        private final boolean valueLoop;
        private final int resultSlot;
        private ValueKind resultKind;
        private String resultStructName;

        private LoopContext(String label, Label continueLabel, Label breakLabel, boolean valueLoop, int resultSlot) {
            this.label = label;
            this.continueLabel = continueLabel;
            this.breakLabel = breakLabel;
            this.valueLoop = valueLoop;
            this.resultSlot = resultSlot;
        }
    }

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

        int allocateTemp() {
            return nextSlot.getAndIncrement();
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
        static StructLayout from(AstStruct struct, Set<String> knownStructs, Set<String> knownEnums) {
            List<FieldInfo> fieldInfos = new ArrayList<>();
            for (AstField field : struct.fields()) {
                fieldInfos.add(FieldInfo.from(field, knownStructs, knownEnums));
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
            if (kind == ValueKind.STRUCT || kind == ValueKind.ENUM) {
                return field.structName().equals(structName);
            }
            return true;
        }
    }

    private record EnumLayout(String name, String internalName, List<EnumVariant> variants) {
        EnumVariant variant(String name) {
            for (EnumVariant variant : variants) {
                if (variant.name().equals(name)) {
                    return variant;
                }
            }
            return null;
        }
    }

    private record EnumVariant(String name, int tag, TypeId payloadType) {}

    private record FieldInfo(String name, String descriptor, ValueKind kind, String structName) {
        static FieldInfo from(AstField field, Set<String> knownStructs, Set<String> knownEnums) {
            String type = field.type();
            return switch (type) {
                case "String", "std::String" -> new FieldInfo(field.name(), "Ljava/lang/String;", ValueKind.STRING, null);
                case "i32", "int" -> new FieldInfo(field.name(), "I", ValueKind.INT, null);
                case "bool" -> new FieldInfo(field.name(), "Z", ValueKind.BOOL, null);
                case "Any", "std::Any" -> new FieldInfo(field.name(), "Ljava/lang/Object;", ValueKind.ANY, null);
                default -> {
                    if (knownStructs.contains(type)) {
                        yield new FieldInfo(field.name(), "L" + type + ";", ValueKind.STRUCT, type);
                    }
                    if (knownEnums.contains(type)) {
                        yield new FieldInfo(field.name(), "L" + type + ";", ValueKind.ENUM, type);
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
