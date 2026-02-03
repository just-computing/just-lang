package org.justlang.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Codegen {
    private static final String MAIN_CLASS_NAME = "Main";
    private static final String MAIN_INTERNAL_NAME = "Main";
    private final Map<String, StructLayout> structLayouts = new HashMap<>();

    public List<ClassFile> emit(AstModule module) {
        buildStructLayouts(module);
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

        Map<String, Local> locals = new HashMap<>();
        int nextLocal = 1;

        for (AstStmt stmt : main.body()) {
            if (stmt instanceof AstLetStmt letStmt) {
                if (letStmt.initializer() == null) {
                    throw new IllegalStateException("let without initializer is not supported");
                }
                ExprValue value = emitExpr(mv, letStmt.initializer(), locals);
                int slot = nextLocal++;
                locals.put(letStmt.name(), new Local(value.kind(), slot, value.structName()));
                storeLocal(mv, value.kind(), slot);
                continue;
            }

            if (stmt instanceof AstExprStmt exprStmt) {
                ExprValue value = emitExpr(mv, exprStmt.expr(), locals);
                if (value.kind() != ValueKind.VOID) {
                    mv.visitInsn(Opcodes.POP);
                }
                continue;
            }

            throw new IllegalStateException("Unsupported statement: " + stmt.getClass().getSimpleName());
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private ExprValue emitExpr(MethodVisitor mv, AstExpr expr, Map<String, Local> locals) {
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
        if (expr instanceof AstCallExpr callExpr) {
            emitCall(mv, callExpr, locals);
            return ExprValue.of(ValueKind.VOID);
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
    }

    private ExprValue emitStructInit(MethodVisitor mv, AstStructInitExpr initExpr, Map<String, Local> locals) {
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

    private ExprValue emitFieldAccess(MethodVisitor mv, AstFieldAccessExpr accessExpr, Map<String, Local> locals) {
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

    private void buildStructLayouts(AstModule module) {
        for (AstItem item : module.items()) {
            if (item instanceof AstStruct struct) {
                structLayouts.put(struct.name(), StructLayout.from(struct, structLayouts));
            }
        }
    }

    private enum ValueKind {
        STRING,
        INT,
        BOOL,
        STRUCT,
        VOID
    }

    private record Local(ValueKind kind, int slot, String structName) {}

    private record ExprValue(ValueKind kind, String structName) {
        static ExprValue of(ValueKind kind) {
            return new ExprValue(kind, null);
        }
    }

    private record StructLayout(String name, String internalName, List<FieldInfo> fields) {
        static StructLayout from(AstStruct struct, Map<String, StructLayout> known) {
            List<FieldInfo> fieldInfos = new ArrayList<>();
            for (AstField field : struct.fields()) {
                fieldInfos.add(FieldInfo.from(field, known));
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
        static FieldInfo from(AstField field, Map<String, StructLayout> known) {
            String type = field.type();
            return switch (type) {
                case "String", "std::String" -> new FieldInfo(field.name(), "Ljava/lang/String;", ValueKind.STRING, null);
                case "i32", "int" -> new FieldInfo(field.name(), "I", ValueKind.INT, null);
                case "bool" -> new FieldInfo(field.name(), "Z", ValueKind.BOOL, null);
                default -> {
                    if (known.containsKey(type)) {
                        yield new FieldInfo(field.name(), "L" + type + ";", ValueKind.STRUCT, type);
                    }
                    throw new IllegalStateException("Unsupported field type: " + type);
                }
            };
        }
    }
}
