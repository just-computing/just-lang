package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

public class CodegenTest {
    @Test
    void emitsMainAndBuiltinEnumsWithExpectedMembers() {
        CodegenTestKit.Compilation compilation = CodegenTestKit.compile("""
            fn main() {
                return;
            }
            """);

        assertTrue(compilation.hasClass("Main"));
        assertTrue(compilation.hasClass("Option"));
        assertTrue(compilation.hasClass("Result"));

        CodegenTestKit.ClassModel main = compilation.inspect("Main");
        assertEquals("Main", main.internalName());
        assertTrue(main.majorVersion() >= 61); // JVM 17+
        assertTrue(main.hasMethod("main", "([Ljava/lang/String;)V"));

        CodegenTestKit.ClassModel option = compilation.inspect("Option");
        assertTrue(option.hasField("tag", "I"));
        assertTrue(option.hasField("payload", "Ljava/lang/Object;"));
        assertTrue(option.hasMethod("Some", "(Ljava/lang/Object;)LOption;"));
        assertTrue(option.hasMethod("None", "()LOption;"));

        CodegenTestKit.ClassModel result = compilation.inspect("Result");
        assertTrue(result.hasMethod("Ok", "(Ljava/lang/Object;)LResult;"));
        assertTrue(result.hasMethod("Err", "(Ljava/lang/Object;)LResult;"));
    }

    @Test
    void runsGenericOptionMatchProgramInMemory() throws Exception {
        int someValue = 7;
        CodegenTestKit.Compilation compilation = CodegenTestKit.compile("""
            fn unwrap_or_zero(x: Option<i32>) -> i32 {
                let out = match x {
                    Option::Some(v) => v,
                    Option::None => 0,
                };
                return out;
            }

            fn main() {
                let x: Option<i32> = Option::Some(%d);
                std::print(unwrap_or_zero(x));
                return;
            }
            """.formatted(someValue));

        CodegenTestKit.MethodModel unwrapOrZero = compilation.inspect("Main").method("unwrap_or_zero", "(LOption;)I");
        assertTrue(unwrapOrZero.containsFieldAccess(Opcodes.GETFIELD, "Option", "tag", "I"));
        assertTrue(unwrapOrZero.containsFieldAccess(Opcodes.GETFIELD, "Option", "payload", "Ljava/lang/Object;"));
        assertTrue(unwrapOrZero.containsTypeOp(Opcodes.CHECKCAST, "java/lang/Integer"));
        assertTrue(unwrapOrZero.containsInvoke(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I"));

        assertEquals(Integer.toString(someValue), compilation.runMainInMemory());
    }

    @Test
    void runsResultMatchProgramViaJar() throws Exception {
        CodegenTestKit.Compilation compilation = CodegenTestKit.compile("""
            fn value_or_default(x: Result<i32, String>) -> i32 {
                let out = match x {
                    Result::Ok(v) => v,
                    Result::Err(_) => 0,
                };
                return out;
            }

            fn main() {
                let ok: Result<i32, String> = Result::Ok(9);
                let err: Result<i32, String> = Result::Err("boom");
                std::print(value_or_default(ok));
                std::print(value_or_default(err));
                return;
            }
            """);

        CodegenTestKit.ClassModel option = compilation.inspect("Option");
        CodegenTestKit.MethodModel optionToString = option.method("toString", "()Ljava/lang/String;");
        assertTrue(optionToString.containsLookupSwitch());

        CodegenTestKit.MethodModel valueOrDefault = compilation.inspect("Main").method("value_or_default", "(LResult;)I");
        assertTrue(valueOrDefault.containsFieldAccess(Opcodes.GETFIELD, "Result", "tag", "I"));
        assertTrue(valueOrDefault.containsFieldAccess(Opcodes.GETFIELD, "Result", "payload", "Ljava/lang/Object;"));

        assertEquals("9\n0", compilation.runMainViaJar());
    }

    @Test
    void emitsPrintInstructionSequence() {
        CodegenTestKit.Compilation compilation = CodegenTestKit.compile("""
            fn main() {
                std::print(42);
                return;
            }
            """);

        CodegenTestKit.MethodModel main = compilation.inspect("Main").method("main", "([Ljava/lang/String;)V");
        assertTrue(main.containsFieldAccess(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        assertTrue(main.containsLdc(42));
        assertTrue(main.containsInvoke(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V"));
        assertTrue(main.containsOpcodeSequence(Opcodes.GETSTATIC, Opcodes.LDC, Opcodes.INVOKEVIRTUAL));
    }

    @Test
    void runsBorrowAndDerefProgram() throws Exception {
        CodegenTestKit.Compilation compilation = CodegenTestKit.compile("""
            fn read(x: &i32) -> i32 {
                return *x;
            }

            fn main() {
                let value = 5;
                let r = &value;
                std::print(read(r));
                std::print(*r);
                return;
            }
            """);

        assertEquals("5\n5", compilation.runMainInMemory());
    }
}
