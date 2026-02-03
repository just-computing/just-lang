package org.justlang.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Codegen {
    private static final String MAIN_CLASS_NAME = "Main";
    private static final String MAIN_INTERNAL_NAME = "Main";

    public List<ClassFile> emit(AstModule module) {
        AstFunction main = findMain(module);
        if (main == null) {
            throw new IllegalStateException("Missing `fn main()`");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, MAIN_INTERNAL_NAME, null, "java/lang/Object", null);

        emitDefaultConstructor(writer);
        emitMainMethod(writer, main);

        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        List<ClassFile> classFiles = new ArrayList<>();
        classFiles.add(new ClassFile(MAIN_CLASS_NAME, bytes));
        return classFiles;
    }

    public String mainClassName() {
        return MAIN_CLASS_NAME;
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

        Map<String, Local> locals = new java.util.HashMap<>();
        int nextLocal = 1;

        for (AstStmt stmt : main.body()) {
            if (stmt instanceof AstLetStmt letStmt) {
                if (letStmt.initializer() == null) {
                    throw new IllegalStateException("let without initializer is not supported");
                }
                ValueType type = emitExpr(mv, letStmt.initializer(), locals);
                int slot = nextLocal++;
                locals.put(letStmt.name(), new Local(type, slot));
                storeLocal(mv, type, slot);
                continue;
            }

            if (stmt instanceof AstExprStmt exprStmt) {
                emitExprStmt(mv, exprStmt.expr(), locals);
                continue;
            }

            throw new IllegalStateException("Unsupported statement: " + stmt.getClass().getSimpleName());
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitExprStmt(MethodVisitor mv, AstExpr expr, Map<String, Local> locals) {
        if (expr instanceof AstCallExpr call) {
            emitCall(mv, call, locals);
            return;
        }
        emitExpr(mv, expr, locals);
        mv.visitInsn(Opcodes.POP);
    }

    private ValueType emitExpr(MethodVisitor mv, AstExpr expr, Map<String, Local> locals) {
        if (expr instanceof AstStringExpr stringExpr) {
            mv.visitLdcInsn(stringExpr.literal());
            return ValueType.STRING;
        }
        if (expr instanceof AstBoolExpr boolExpr) {
            mv.visitInsn(boolExpr.value() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            return ValueType.BOOL;
        }
        if (expr instanceof AstNumberExpr numberExpr) {
            int value = Integer.parseInt(numberExpr.literal());
            mv.visitLdcInsn(value);
            return ValueType.INT;
        }
        if (expr instanceof AstIdentExpr identExpr) {
            Local local = locals.get(identExpr.name());
            if (local == null) {
                throw new IllegalStateException("Unknown identifier: " + identExpr.name());
            }
            loadLocal(mv, local.type(), local.slot());
            return local.type();
        }
        throw new IllegalStateException("Unsupported expression: " + expr.getClass().getSimpleName());
    }

    private void emitCall(MethodVisitor mv, AstCallExpr call, Map<String, Local> locals) {
        if (!isPrintCall(call)) {
            throw new IllegalStateException("Only std::print(...) is supported in v1");
        }
        if (call.args().size() != 1) {
            throw new IllegalStateException("print expects exactly one argument");
        }
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        ValueType argType = emitExpr(mv, call.args().get(0), locals);
        if (argType == ValueType.STRING) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        } else if (argType == ValueType.INT) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        } else if (argType == ValueType.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
        } else {
            throw new IllegalStateException("Unsupported print argument type: " + argType);
        }
    }

    private void storeLocal(MethodVisitor mv, ValueType type, int slot) {
        if (type == ValueType.STRING) {
            mv.visitVarInsn(Opcodes.ASTORE, slot);
            return;
        }
        if (type == ValueType.INT) {
            mv.visitVarInsn(Opcodes.ISTORE, slot);
            return;
        }
        throw new IllegalStateException("Unsupported local type: " + type);
    }

    private void loadLocal(MethodVisitor mv, ValueType type, int slot) {
        if (type == ValueType.STRING) {
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            return;
        }
        if (type == ValueType.INT) {
            mv.visitVarInsn(Opcodes.ILOAD, slot);
            return;
        }
        throw new IllegalStateException("Unsupported local type: " + type);
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

    private enum ValueType {
        STRING,
        INT,
        BOOL,
        VOID
    }

    private record Local(ValueType type, int slot) {}
}
