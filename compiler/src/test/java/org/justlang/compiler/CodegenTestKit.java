package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
        return new Compilation(module, files, bytecode);
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

        private static Path javaExecutable() {
            String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            return Path.of(System.getProperty("java.home"), "bin", exe);
        }
    }

    static final class ClassModel {
        private final String internalName;
        private final int majorVersion;
        private final Map<String, String> fields;
        private final Map<String, List<String>> methods;

        private ClassModel(String internalName, int majorVersion, Map<String, String> fields, Map<String, List<String>> methods) {
            this.internalName = internalName;
            this.majorVersion = majorVersion;
            this.fields = fields;
            this.methods = methods;
        }

        static ClassModel from(byte[] bytes) {
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, List<String>> methods = new LinkedHashMap<>();
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
                    methods.computeIfAbsent(name, ignored -> new ArrayList<>()).add(descriptor);
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
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
            List<String> descriptors = methods.get(name);
            return descriptors != null && descriptors.contains(descriptor);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
