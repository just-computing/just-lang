package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class TypeCheckerTest {
    @Test
    void enumMatchExhaustiveHasNoWarnings() {
        TypeResult result = typeCheck("""
            enum Choice { A, B }

            fn main() {
                let value = Choice::A;
                let out = match value {
                    Choice::A => 1,
                    Choice::B => 2,
                };
                std::print(out);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
        assertEquals(List.of(), result.environment().warnings());
    }

    @Test
    void enumMatchNonExhaustiveWarns() {
        TypeResult result = typeCheck("""
            enum Choice { A, B }

            fn main() {
                let value = Choice::A;
                let out = match value {
                    Choice::A => 1,
                };
                std::print(out);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
        assertEquals(1, result.environment().warnings().size());
        assertTrue(result.environment().warnings().get(0).contains("missing Choice::B"));
    }

    @Test
    void nonEnumMatchNonExhaustiveWarns() {
        TypeResult result = typeCheck("""
            fn main() {
                let value = 1;
                let out = match value {
                    1 => 1,
                };
                std::print(out);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
        assertEquals(1, result.environment().warnings().size());
        assertTrue(result.environment().warnings().get(0).contains("missing '_'"));
    }

    @Test
    void matchWildcardMustBeLast() {
        TypeResult result = typeCheck("""
            fn main() {
                let out = match 1 {
                    _ => 1,
                    2 => 2,
                };
                std::print(out);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("wildcard '_' must be the last match arm")));
    }

    @Test
    void matchPatternTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let out = match 1 {
                    Option::Some(x) => x,
                    _ => 0,
                };
                std::print(out);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("match pattern does not match target type")));
    }

    @Test
    void rangePatternValidation() {
        TypeResult result = typeCheck("""
            fn main() {
                let out = match 3 {
                    5..=1 => 1,
                    _ => 0,
                };
                std::print(out);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("match range start must be <= end")));
    }

    @Test
    void ifLetBindsEnumPayload() {
        TypeResult result = typeCheck("""
            enum Choice { A(i32), B }

            fn main() {
                let value = Choice::A(5);
                if let Choice::A(x) = value {
                    std::print(x);
                } else {
                    std::print(0);
                }
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void whileLetBindsEnumPayload() {
        TypeResult result = typeCheck("""
            fn main() {
                let mut value = Option::Some(1);
                while let Option::Some(x) = value {
                    std::print(x);
                    value = Option::None;
                }
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void ifConditionMustBeBool() {
        TypeResult result = typeCheck("""
            fn main() {
                if 1 {
                    std::print(1);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("if condition must be bool")));
    }

    @Test
    void whileConditionMustBeBool() {
        TypeResult result = typeCheck("""
            fn main() {
                while 1 {
                    std::print(1);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("while condition must be bool")));
    }

    @Test
    void forLoopRequiresIntBounds() {
        TypeResult result = typeCheck("""
            fn main() {
                for i in true..false {
                    std::print(i);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("for loop bounds must be int")));
    }

    @Test
    void assignmentRequiresMutable() {
        TypeResult result = typeCheck("""
            fn main() {
                let value = 1;
                value = 2;
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Cannot assign to immutable variable")));
    }

    @Test
    void compoundAssignmentRequiresInt() {
        TypeResult result = typeCheck("""
            fn main() {
                let mut value = true;
                value += false;
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Compound assignment requires int operands")));
    }

    @Test
    void returnTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn foo() -> i32 {
                return true;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("return type mismatch")));
    }

    @Test
    void nonVoidFunctionMustReturn() {
        TypeResult result = typeCheck("""
            fn foo() -> i32 {
                let x = 1;
                std::print(x);
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("must return on all paths")));
    }

    @Test
    void loopExpressionRequiresBreakValue() {
        TypeResult result = typeCheck("""
            fn main() -> i32 {
                let x = loop {
                    break;
                };
                return x;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err ->
            err.contains("loop expression requires break with value")
                || err.contains("break value required for loop expression")
        ));
    }

    @Test
    void breakOutsideLoopFails() {
        TypeResult result = typeCheck("""
            fn main() {
                break;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("break is only valid inside loops")));
    }

    @Test
    void continueOutsideLoopFails() {
        TypeResult result = typeCheck("""
            fn main() {
                continue;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("continue is only valid inside loops")));
    }

    @Test
    void printRequiresPrintable() {
        TypeResult result = typeCheck("""
            fn returns_void() {
                return;
            }

            fn main() {
                std::print(returns_void());
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("print does not support type")));
    }

    @Test
    void duplicateFunctionFails() {
        TypeResult result = typeCheck("""
            fn main() { return; }
            fn main() { return; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Duplicate function")));
    }

    @Test
    void mainCannotHaveParameters() {
        TypeResult result = typeCheck("""
            fn main(x: i32) { return; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("main does not accept parameters")));
    }

    @Test
    void mainMustReturnVoid() {
        TypeResult result = typeCheck("""
            fn main() -> i32 { return 1; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("main must return void")));
    }

    @Test
    void structInitAndFieldAccess() {
        TypeResult result = typeCheck("""
            struct Point { x: i32, y: i32 }

            fn main() {
                let p = Point { x: 1, y: 2 };
                std::print(p.x);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void structInitMissingFieldFails() {
        TypeResult result = typeCheck("""
            struct Point { x: i32, y: i32 }

            fn main() {
                let p = Point { x: 1 };
                std::print(p.x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Missing field 'y'")));
    }

    @Test
    void structInitUnknownFieldFails() {
        TypeResult result = typeCheck("""
            struct Point { x: i32, y: i32 }

            fn main() {
                let p = Point { x: 1, y: 2, z: 3 };
                std::print(p.x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown field 'z'")));
    }

    @Test
    void fieldAccessOnNonStructFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = 1;
                std::print(x.y);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Field access on non-struct type")));
    }

    @Test
    void ifExprBranchesMustMatch() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = if true { 1 } else { false };
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("if expression branches must match")));
    }

    @Test
    void unaryOperatorsTypeCheck() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = -true;
                let y = !1;
                std::print(x);
                std::print(y);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unary - requires int operand")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unary ! requires bool operand")));
    }

    @Test
    void binaryOperatorTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = 1 + false;
                let y = 1 < false;
                let z = 1 == false;
                std::print(x);
                std::print(y);
                std::print(z);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Arithmetic operator requires int operands")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Comparison operator requires int operands")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Equality requires matching operand types")));
    }

    @Test
    void logicalOperatorsRequireBool() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = 1 && 2;
                let y = 1 || 2;
                std::print(x);
                std::print(y);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Logical operator requires bool operands")));
    }

    @Test
    void unknownFunctionAndWrongArity() {
        TypeResult result = typeCheck("""
            fn add(a: i32, b: i32) -> i32 { return a + b; }

            fn main() {
                let x = add(1);
                let y = missing(1, 2);
                std::print(x);
                std::print(y);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("expects 2 arguments")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown function: missing")));
    }

    @Test
    void enumVariantCallValidation() {
        TypeResult result = typeCheck("""
            enum Choice { A(i32), B }

            fn main() {
                let a = Choice::A();
                let b = Choice::B(1);
                std::print(a);
                std::print(b);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("expects one argument")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("does not take a value")));
    }

    @Test
    void enumVariantArgTypeMismatchFails() {
        TypeResult result = typeCheck("""
            enum Choice { A(i32) }

            fn main() {
                let a = Choice::A(true);
                std::print(a);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("expects Int got Bool")));
    }

    @Test
    void matchRequiresArms() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = match 1 { };
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("match requires at least one arm")));
    }

    @Test
    void matchArmTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = match 1 {
                    1 => 1,
                    _ => true,
                };
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("match arms must return the same type")));
    }

    @Test
    void unknownIdentifierFails() {
        TypeResult result = typeCheck("""
            fn main() {
                std::print(missing);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown identifier")));
    }

    @Test
    void typeAnnotationMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x: bool = 1;
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Type mismatch in let binding")));
    }

    @Test
    void unknownTypeInLetFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x: Missing = 1;
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown type: Missing")));
    }

    @Test
    void unknownTypeInParamFails() {
        AstParam param = new AstParam("x", "Missing", false);
        AstFunction fn = new AstFunction("foo", List.of(param), null, List.of(new AstReturnStmt(null)));
        AstModule module = new AstModule(List.of(fn));
        TypeResult result = new TypeChecker().typeCheck(module);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown parameter type: Missing")));
    }

    @Test
    void unsupportedItemFails() {
        AstItem fake = new AstItem() {};
        AstModule module = new AstModule(List.of(fake));
        TypeResult result = new TypeChecker().typeCheck(module);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported item")));
    }

    @Test
    void letWithoutInitializerFails() {
        AstLetStmt badLet = new AstLetStmt("x", false, null, null);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(badLet));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("let without initializer")));
    }

    @Test
    void hirTypeCheckIsNotImplementedYet() {
        TypeChecker checker = new TypeChecker();
        assertThrows(UnsupportedOperationException.class, () -> checker.typeCheck((HirModule) null));
    }

    @Test
    void breakValueOnlyAllowedInLoopExpressions() {
        TypeResult result = typeCheck("""
            fn main() {
                while true {
                    break 1;
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("break with value is only allowed in loop expressions")));
    }

    @Test
    void loopExpressionBreakValuesMustMatch() {
        TypeResult result = typeCheck("""
            fn main() -> i32 {
                let x = loop {
                    if true {
                        break 1;
                    } else {
                        break true;
                    }
                };
                return x;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("break values in loop expression must have the same type")));
    }

    @Test
    void unknownLoopLabelFails() {
        TypeResult result = typeCheck("""
            fn main() {
                loop {
                    break 'missing;
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown loop label 'missing'")));
    }

    @Test
    void enumPatternBindingOnUnitVariantFails() {
        TypeResult result = typeCheck("""
            enum Choice { A, B(i32) }

            fn main() {
                let v = Choice::A;
                if let Choice::A(x) = v {
                    std::print(x);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("does not bind a value")));
    }

    @Test
    void ifLetPatternMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let v = 1;
                if let Option::Some(x) = v {
                    std::print(x);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("if let pattern does not match target type")));
    }

    @Test
    void whileLetPatternMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let mut v = 1;
                while let Option::Some(x) = v {
                    v = x;
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("while let pattern does not match target type")));
    }

    @Test
    void unsupportedStatementNodeFails() {
        AstStmt unknownStmt = new AstStmt() {};
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(unknownStmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported statement")));
    }

    @Test
    void unsupportedExpressionNodeFails() {
        AstExpr unknownExpr = new AstExpr() {};
        AstExprStmt stmt = new AstExprStmt(unknownExpr);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(stmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported expression")));
    }

    @Test
    void unsupportedUnaryOperatorFails() {
        AstUnaryExpr expr = new AstUnaryExpr("~", new AstNumberExpr("1"));
        AstExprStmt stmt = new AstExprStmt(expr);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(stmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported unary operator")));
    }

    @Test
    void unsupportedCallShapeFails() {
        AstCallExpr call = new AstCallExpr(List.of("a", "b", "c"), List.of());
        AstExprStmt stmt = new AstExprStmt(call);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(stmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Only direct function calls are supported")));
    }

    @Test
    void nonVoidFunctionReturnsOnAllPathsSucceeds() {
        TypeResult result = typeCheck("""
            fn foo(x: i32) -> i32 {
                if x > 0 {
                    return 1;
                } else {
                    return 2;
                }
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void matchOnBoolAndStringPatterns() {
        TypeResult result = typeCheck("""
            fn main() {
                let b = match true {
                    true => 1,
                    false => 0,
                };
                let s = match "x" {
                    "x" => 1,
                    _ => 0,
                };
                std::print(b);
                std::print(s);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void enumPathVariantRequiringValueFails() {
        TypeResult result = typeCheck("""
            enum Choice { A(i32) }

            fn main() {
                let x = Choice::A;
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("requires a value")));
    }

    @Test
    void unsupportedPathExpressionFails() {
        AstPathExpr expr = new AstPathExpr(List.of("a", "b", "c"));
        AstExprStmt stmt = new AstExprStmt(expr);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(stmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported path expression")));
    }

    @Test
    void enumCallUnknownEnumAndVariantFail() {
        TypeResult result = typeCheck("""
            enum Choice { A(i32) }

            fn main() {
                let x = Missing::A(1);
                let y = Choice::B(1);
                std::print(x);
                std::print(y);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown enum: Missing")));
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown variant 'B' on enum Choice")));
    }

    @Test
    void unknownEnumPayloadTypeInCallFails() {
        TypeResult result = typeCheck("""
            enum Broken { X(Missing) }

            fn main() {
                let x = Broken::X(1);
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown payload type: Missing")));
    }

    @Test
    void unknownEnumPayloadTypeInPatternFails() {
        TypeResult result = typeCheck("""
            enum Broken { X(Missing) }

            fn main() {
                let x = Broken::X(1);
                if let Broken::X(v) = x {
                    std::print(v);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown payload type: Missing")));
    }

    @Test
    void enumPatternOnDifferentEnumFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x = Result::Ok(1);
                if let Option::Some(v) = x {
                    std::print(v);
                }
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("if let pattern does not match target type")));
    }

    @Test
    void labeledLoopBreakAndContinueSucceed() {
        TypeResult result = typeCheck("""
            fn main() {
                'outer: loop {
                    continue 'outer;
                    break 'outer;
                }
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void reservedEnumNameFails() {
        TypeResult result = typeCheck("""
            enum Option { X }
            fn main() { return; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("reserved or already defined")));
    }

    @Test
    void voidParameterTypeFails() {
        TypeResult result = typeCheck("""
            fn foo(x: void) { return; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Parameter type cannot be void")));
    }

    @Test
    void unknownReturnTypeFails() {
        TypeResult result = typeCheck("""
            fn foo() -> Missing { return 1; }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown type: Missing")));
    }

    @Test
    void letTypeVoidFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let x: void = 1;
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("let type cannot be void")));
    }

    @Test
    void letInitializerVoidFails() {
        TypeResult result = typeCheck("""
            fn noop() { return; }
            fn main() {
                let x = noop();
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("let initializer cannot be void")));
    }

    @Test
    void assignmentUnknownIdentifierFails() {
        TypeResult result = typeCheck("""
            fn main() {
                x = 1;
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unknown identifier: x")));
    }

    @Test
    void assignmentValueVoidFails() {
        TypeResult result = typeCheck("""
            fn noop() { return; }
            fn main() {
                let mut x = 1;
                x = noop();
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("assignment value cannot be void")));
    }

    @Test
    void assignmentTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn main() {
                let mut x = 1;
                x = true;
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Type mismatch in assignment")));
    }

    @Test
    void printArityValidation() {
        TypeResult result = typeCheck("""
            fn main() {
                std::print(1, 2);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("print expects exactly one argument")));
    }

    @Test
    void functionArgumentTypeMismatchFails() {
        TypeResult result = typeCheck("""
            fn foo(x: i32) { return; }
            fn main() {
                foo(true);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Argument 1 of 'foo' expected Int got Bool")));
    }

    @Test
    void optionSomeCannotTakeVoid() {
        TypeResult result = typeCheck("""
            fn noop() { return; }
            fn main() {
                let x = Option::Some(noop());
                std::print(x);
                return;
            }
            """);

        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("cannot take void")));
    }

    @Test
    void unsupportedBinaryOperatorFails() {
        AstBinaryExpr expr = new AstBinaryExpr(new AstNumberExpr("1"), "%", new AstNumberExpr("2"));
        AstExprStmt stmt = new AstExprStmt(expr);
        AstFunction fn = new AstFunction("main", List.of(), null, List.of(stmt));
        AstModule module = new AstModule(List.of(fn));

        TypeResult result = new TypeChecker().typeCheck(module);
        assertFalse(result.success(), "expected type check to fail");
        assertTrue(result.environment().errors().stream().anyMatch(err -> err.contains("Unsupported operator: %")));
    }

    @Test
    void bindEnumPatternInternalBranches() throws Exception {
        TypeChecker checker = new TypeChecker();
        Method bind = TypeChecker.class.getDeclaredMethod(
            "bindEnumPattern",
            AstMatchPattern.class,
            TypeId.class,
            TypeEnvironment.class,
            StructRegistry.class,
            EnumRegistry.class,
            TypeEnvironment.class
        );
        bind.setAccessible(true);

        TypeEnvironment diagnostics = new TypeEnvironment();
        boolean nonEnum = (boolean) bind.invoke(
            checker,
            AstMatchPattern.enumVariant("Option", "Some", "v"),
            TypeId.INT,
            new TypeEnvironment(),
            new StructRegistry(),
            new EnumRegistry(),
            diagnostics
        );
        assertFalse(nonEnum);
        assertTrue(diagnostics.errors().stream().anyMatch(err -> err.contains("enum pattern used on non-enum type")));

        EnumRegistry enums = new EnumRegistry();
        enums.register(new AstEnum("Broken", List.of(new AstEnumVariant("X", "Missing"))));
        TypeEnvironment diagnostics2 = new TypeEnvironment();
        boolean unknownPayload = (boolean) bind.invoke(
            checker,
            AstMatchPattern.enumVariant("Broken", "X", "v"),
            TypeId.enumType("Broken"),
            new TypeEnvironment(),
            new StructRegistry(),
            enums,
            diagnostics2
        );
        assertFalse(unknownPayload);
        assertTrue(diagnostics2.errors().stream().anyMatch(err -> err.contains("Unknown payload type: Missing")));
    }

    @Test
    void validUnaryOperatorsSucceed() {
        TypeResult result = typeCheck("""
            fn main() {
                let a = -1;
                let b = !false;
                std::print(a);
                std::print(b);
                return;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void loopExpressionWithBreakValueSucceeds() {
        TypeResult result = typeCheck("""
            fn foo() -> i32 {
                let x = loop {
                    break 1;
                };
                return x;
            }
            """);

        assertTrue(result.success(), "expected type check to succeed");
    }

    @Test
    void bindEnumPatternMismatchAndUnknownVariantBranches() throws Exception {
        TypeChecker checker = new TypeChecker();
        Method bind = TypeChecker.class.getDeclaredMethod(
            "bindEnumPattern",
            AstMatchPattern.class,
            TypeId.class,
            TypeEnvironment.class,
            StructRegistry.class,
            EnumRegistry.class,
            TypeEnvironment.class
        );
        bind.setAccessible(true);

        EnumRegistry enums = new EnumRegistry();
        enums.register(new AstEnum("A", List.of(new AstEnumVariant("X", null))));
        enums.register(new AstEnum("B", List.of(new AstEnumVariant("Y", null))));

        TypeEnvironment diagnostics = new TypeEnvironment();
        boolean mismatch = (boolean) bind.invoke(
            checker,
            AstMatchPattern.enumVariant("A", "X", null),
            TypeId.enumType("B"),
            new TypeEnvironment(),
            new StructRegistry(),
            enums,
            diagnostics
        );
        assertFalse(mismatch);
        assertTrue(diagnostics.errors().stream().anyMatch(err -> err.contains("enum pattern does not match target enum")));

        TypeEnvironment diagnostics2 = new TypeEnvironment();
        boolean unknownVariant = (boolean) bind.invoke(
            checker,
            AstMatchPattern.enumVariant("A", "Missing", null),
            TypeId.enumType("A"),
            new TypeEnvironment(),
            new StructRegistry(),
            enums,
            diagnostics2
        );
        assertFalse(unknownVariant);
        assertTrue(diagnostics2.errors().stream().anyMatch(err -> err.contains("Unknown variant 'Missing' on enum A")));
    }

    private TypeResult typeCheck(String source) {
        Diagnostics diagnostics = new Diagnostics();
        SourceFile sourceFile = new SourceFile(Path.of("test.just"), source);
        Lexer lexer = new Lexer();
        Parser parser = new Parser();
        TypeChecker checker = new TypeChecker();
        var tokens = lexer.lex(sourceFile, diagnostics);
        AstModule module = parser.parse(sourceFile, tokens, diagnostics);
        return checker.typeCheck(module);
    }
}
