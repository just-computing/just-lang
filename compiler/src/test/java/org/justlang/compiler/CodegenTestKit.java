package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Printer;

final class CodegenTestKit {
    private CodegenTestKit() {}

    static Compilation compile(String source) {
        Diagnostics diagnostics = new Diagnostics();
        SourceFile sourceFile = new SourceFile(Path.of("test.just"), source);
        Lexer lexer = new Lexer();
        Parser parser = new Parser();
        TypeChecker checker = new TypeChecker();
        Codegen codegen = new Codegen();

        var tokens = lexer.lex(sourceFile, diagnostics);
        AstModule module = parser.parse(sourceFile, tokens, diagnostics);
        TypeResult typeResult = checker.typeCheck(module);
        assertTrue(typeResult.success(), "type checker failed: " + typeResult.environment().errors());

        List<ClassFile> files = codegen.emit(module);
        Map<String, byte[]> bytecode = new HashMap<>();
        for (ClassFile file : files) {
            bytecode.put(file.internalName(), file.bytes());
        }
        Compilation compilation = new Compilation(module, files, bytecode);
        compilation.verifyBytecode();
        return compilation;
    }

    static final class Compilation {
        private final AstModule module;
        private final List<ClassFile> files;
        private final Map<String, byte[]> bytecode;

        private Compilation(AstModule module, List<ClassFile> files, Map<String, byte[]> bytecode) {
            this.module = module;
            this.files = files;
            this.bytecode = bytecode;
        }

        AstModule module() {
            return module;
        }

        List<ClassFile> files() {
            return files;
        }

        boolean hasClass(String internalName) {
            return bytecode.containsKey(internalName);
        }

        ClassModel inspect(String internalName) {
            byte[] bytes = bytecode.get(internalName);
            if (bytes == null) {
                throw new IllegalArgumentException("Missing generated class: " + internalName);
            }
            return ClassModel.from(bytes);
        }

        String runMainInMemory() throws Exception {
            ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = bytecode.get(name);
                    if (bytes != null) {
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    return super.findClass(name);
                }
            };

            Class<?> mainClass = loader.loadClass("Main");
            Method main = mainClass.getMethod("main", String[].class);

            PrintStream originalOut = System.out;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (PrintStream capture = new PrintStream(buffer)) {
                System.setOut(capture);
                main.invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(originalOut);
            }
            return normalize(buffer.toString(StandardCharsets.UTF_8));
        }

        String runMainViaJar() throws Exception {
            Path jar = Files.createTempFile("just-codegen-", ".jar");
            try {
                new JarEmitter().writeJar(files, jar, "Main");
                Process process = new ProcessBuilder(javaExecutable().toString(), "-jar", jar.toString())
                    .redirectErrorStream(true)
                    .start();
                byte[] output = process.getInputStream().readAllBytes();
                int exit = process.waitFor();
                String text = normalize(new String(output, StandardCharsets.UTF_8));
                assertTrue(exit == 0, "java -jar failed with exit " + exit + ": " + text);
                return text;
            } finally {
                Files.deleteIfExists(jar);
            }
        }

        void verifyBytecode() {
            ClassLoader verifierLoader = new ClassLoader(CodegenTestKit.class.getClassLoader()) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = bytecode.get(name.replace('.', '/'));
                    if (bytes == null) {
                        bytes = bytecode.get(name);
                    }
                    if (bytes != null) {
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    return super.findClass(name);
                }
            };

            for (Map.Entry<String, byte[]> entry : bytecode.entrySet()) {
                String internalName = entry.getKey();
                StringWriter diagnostics = new StringWriter();
                try (PrintWriter writer = new PrintWriter(diagnostics)) {
                    CheckClassAdapter.verify(new ClassReader(entry.getValue()), verifierLoader, false, writer);
                } catch (Exception ex) {
                    throw new AssertionError("ASM verifier crashed for class " + internalName, ex);
                }
                String verifierOutput = diagnostics.toString().trim();
                assertTrue(verifierOutput.isEmpty(), () ->
                    "ASM verifier found invalid bytecode in " + internalName + ":\n" + verifierOutput);
            }
        }

        private static Path javaExecutable() {
            String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            return Path.of(System.getProperty("java.home"), "bin", exe);
        }
    }

    static final class ClassModel {
        private final String internalName;
        private final int majorVersion;
        private final Map<String, String> fields;
        private final Map<String, MethodModel> methods;

        private ClassModel(String internalName, int majorVersion, Map<String, String> fields, Map<String, MethodModel> methods) {
            this.internalName = internalName;
            this.majorVersion = majorVersion;
            this.fields = fields;
            this.methods = methods;
        }

        static ClassModel from(byte[] bytes) {
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, MethodModel> methods = new LinkedHashMap<>();
            final String[] className = new String[1];
            final int[] classVersion = new int[1];

            ClassReader reader = new ClassReader(bytes);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    className[0] = name;
                    classVersion[0] = version;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    fields.put(name, descriptor);
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    List<Instruction> instructions = new ArrayList<>();
                    methods.put(methodKey(name, descriptor), new MethodModel(name, descriptor, instructions));
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInsn(int opcode) {
                            instructions.add(Instruction.opcode(opcode));
                        }

                        @Override
                        public void visitIntInsn(int opcode, int operand) {
                            instructions.add(Instruction.intOp(opcode, operand));
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            instructions.add(Instruction.typeOp(opcode, type));
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            instructions.add(Instruction.fieldOp(opcode, owner, name, descriptor));
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            instructions.add(Instruction.invoke(opcode, owner, name, descriptor, isInterface));
                        }

                        @Override
                        public void visitJumpInsn(int opcode, Label label) {
                            instructions.add(Instruction.jump(opcode));
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            instructions.add(Instruction.ldc(value));
                        }

                        @Override
                        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                            instructions.add(Instruction.tableSwitch(min, max));
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                            instructions.add(Instruction.lookupSwitch(keys == null ? 0 : keys.length));
                        }
                    };
                }
            }, 0);

            return new ClassModel(className[0], classVersion[0], fields, methods);
        }

        String internalName() {
            return internalName;
        }

        int majorVersion() {
            return majorVersion;
        }

        boolean hasField(String name, String descriptor) {
            return descriptor.equals(fields.get(name));
        }

        boolean hasMethod(String name, String descriptor) {
            return methods.containsKey(methodKey(name, descriptor));
        }

        MethodModel method(String name, String descriptor) {
            MethodModel method = methods.get(methodKey(name, descriptor));
            if (method == null) {
                throw new IllegalArgumentException("Missing generated method: " + name + descriptor);
            }
            return method;
        }

        private static String methodKey(String name, String descriptor) {
            return name + descriptor;
        }
    }

    static final class MethodModel {
        private final String name;
        private final String descriptor;
        private final List<Instruction> instructions;

        private MethodModel(String name, String descriptor, List<Instruction> instructions) {
            this.name = name;
            this.descriptor = descriptor;
            this.instructions = instructions;
        }

        String name() {
            return name;
        }

        String descriptor() {
            return descriptor;
        }

        List<Instruction> instructions() {
            return Collections.unmodifiableList(instructions);
        }

        boolean containsOpcode(int opcode) {
            return instructions.stream().anyMatch(insn -> insn.opcode() == opcode);
        }

        long countOpcode(int opcode) {
            return instructions.stream().filter(insn -> insn.opcode() == opcode).count();
        }

        boolean containsInvoke(int opcode, String owner, String method, String methodDescriptor) {
            return instructions.stream().anyMatch(insn ->
                insn.opcode() == opcode
                    && owner.equals(insn.owner())
                    && method.equals(insn.member())
                    && methodDescriptor.equals(insn.descriptor()));
        }

        boolean containsFieldAccess(int opcode, String owner, String field, String fieldDescriptor) {
            return instructions.stream().anyMatch(insn ->
                insn.opcode() == opcode
                    && owner.equals(insn.owner())
                    && field.equals(insn.member())
                    && fieldDescriptor.equals(insn.descriptor()));
        }

        boolean containsTypeOp(int opcode, String type) {
            return instructions.stream().anyMatch(insn ->
                insn.opcode() == opcode && type.equals(insn.type()));
        }

        boolean containsLdc(Object value) {
            return instructions.stream().anyMatch(insn -> Objects.equals(value, insn.literal()));
        }

        boolean containsLookupSwitch() {
            return containsOpcode(Opcodes.LOOKUPSWITCH);
        }

        boolean containsTableSwitch() {
            return containsOpcode(Opcodes.TABLESWITCH);
        }

        boolean containsOpcodeSequence(int... opcodes) {
            if (opcodes.length == 0) {
                return true;
            }
            int cursor = 0;
            for (Instruction instruction : instructions) {
                if (instruction.opcode() == opcodes[cursor]) {
                    cursor++;
                    if (cursor == opcodes.length) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static final class Instruction {
        private final int opcode;
        private final String owner;
        private final String member;
        private final String descriptor;
        private final String type;
        private final Object literal;
        private final Integer operand;

        private Instruction(int opcode, String owner, String member, String descriptor, String type, Object literal, Integer operand) {
            this.opcode = opcode;
            this.owner = owner;
            this.member = member;
            this.descriptor = descriptor;
            this.type = type;
            this.literal = literal;
            this.operand = operand;
        }

        static Instruction opcode(int opcode) {
            return new Instruction(opcode, null, null, null, null, null, null);
        }

        static Instruction intOp(int opcode, int operand) {
            return new Instruction(opcode, null, null, null, null, null, operand);
        }

        static Instruction typeOp(int opcode, String type) {
            return new Instruction(opcode, null, null, null, type, null, null);
        }

        static Instruction fieldOp(int opcode, String owner, String name, String descriptor) {
            return new Instruction(opcode, owner, name, descriptor, null, null, null);
        }

        static Instruction invoke(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            return new Instruction(opcode, owner, name, descriptor, isInterface ? "interface" : "class", null, null);
        }

        static Instruction jump(int opcode) {
            return new Instruction(opcode, null, null, null, null, null, null);
        }

        static Instruction ldc(Object literal) {
            return new Instruction(Opcodes.LDC, null, null, null, null, literal, null);
        }

        static Instruction tableSwitch(int min, int max) {
            return new Instruction(Opcodes.TABLESWITCH, null, null, null, null, null, max - min + 1);
        }

        static Instruction lookupSwitch(int keyCount) {
            return new Instruction(Opcodes.LOOKUPSWITCH, null, null, null, null, null, keyCount);
        }

        int opcode() {
            return opcode;
        }

        String owner() {
            return owner;
        }

        String member() {
            return member;
        }

        String descriptor() {
            return descriptor;
        }

        String type() {
            return type;
        }

        Object literal() {
            return literal;
        }

        Integer operand() {
            return operand;
        }

        String opcodeName() {
            if (opcode < 0 || opcode >= Printer.OPCODES.length) {
                return "OP_" + opcode;
            }
            return Printer.OPCODES[opcode];
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
