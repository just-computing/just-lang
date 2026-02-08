# Compiler Module (`compiler/`)

## Purpose

Parses Just source, performs name/type/borrow analysis, lowers to IR, monomorphizes generics, and emits JVM bytecode packaged into `.jar` outputs.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `JustCompiler` | End-to-end compilation orchestrator. | `compile(CompileRequest): CompileResult` |
| `CompilerConfig` | Compiler settings, target, and feature flags. | `fromToml(Path): CompilerConfig` |
| `SourceLoader` | Reads source files and builds module graph from explicit imports. | `load(Project): List<SourceFile>`, `loadFileGraph(Path): List<SourceFile>` |
| `Lexer` | Tokenizes source text. | `lex(SourceFile): List<Token>` |
| `Parser` | Builds AST from tokens. | `parse(List<Token>): AstModule` |
| `NameResolver` | Resolves symbols to bindings. | `resolve(AstModule): HirModule` |
| `TypeChecker` | Infers and checks types. | `typeCheck(HirModule): TypedModule` |
| `BorrowFlowAnalyzer` | Applies borrow rules over lexical control flow (bind/borrow/move/assign). | `recordPersistentBorrow(...): boolean` |
| `BorrowAnalyzer` | High-level borrow API used by `BorrowFlowAnalyzer` (`validateMove`, `validateAssignment`, `validateBorrow`). | `validateMove(String): BorrowValidation` |
| `LexicalBorrowAnalyzer` | Default borrow analyzer implementation (v1), delegates state to `BorrowTracker`. | `recordBorrow(...): void` |
| `BorrowTracker` | Low-level lexical counters/scopes abstraction. | `addBindingBorrow(...): void` |
| `BorrowChecker` | Enforces ownership/borrowing rules. | `check(TypedModule): BorrowResult` |
| `MirBuilder` | Lowers typed HIR to MIR. | `lower(TypedModule): MirModule` |
| `Monomorphizer` | Specializes generics. | `specialize(MirModule): MirModule` |
| `Codegen` | Emits JVM bytecode. | `emit(MirModule): List<ClassFile>` |
| `JarEmitter` | Writes `.jar` with manifest and classes. | `writeJar(List<ClassFile>, Path): void` |
| `Diagnostics` | Collects and formats errors. | `report(Diagnostic): void` |

## Data Flow

1. `SourceLoader` reads project sources:
   - directory mode scans `.just` files
   - file mode resolves transitive `import "path.just";` dependencies
2. `Lexer` produces tokens from each source file.
3. `Parser` builds AST (`AstModule` and items).
4. `NameResolver` produces HIR with resolved bindings.
5. `TypeChecker` infers/checks types and produces `TypedModule`.
6. `TypeChecker` uses `BorrowFlowAnalyzer`, which delegates policy to `BorrowAnalyzer` and state to `BorrowTracker`.
7. `BorrowChecker` validates ownership and lifetimes.
8. `MirBuilder` lowers to MIR.
9. `Monomorphizer` specializes generics.
10. `Codegen` emits JVM class files.
11. `JarEmitter` packages class files into a runnable `.jar`.

## Outputs

- `.jar` file with generated JVM classes
- `Diagnostics` for errors/warnings

## Testing

- E2E compiler fixtures live in `compiler/src/test/resources/fixtures`.
- The fixture runner is `compiler/src/test/java/org/justlang/compiler/CompilerFixtureTest.java`.
- Fixture execution logic is in `compiler/src/test/java/org/justlang/compiler/CompilerFixtureKit.java`.
- Full setup and usage are documented in `docs/COMPILER_TESTING.md`.

## Current Language Surface (Prototype)

- Functions with typed parameters and return types (`fn add(a: i32, b: i32) -> i32`).
- Top-level file imports (`import "path.just";`) with transitive resolution.
- `if` / `else if` / `else` statements and `if` expressions.
- `while`, `for i in 0..N` / `0..=N`, and `loop {}` (infinite loop).
- `break`, `break <expr>` (loop expressions only), `continue`, and optional loop labels (`'outer:`).
- `if let` / `while let` statements for pattern matching.
- `match` expression with literal patterns (`int`, `bool`, `String`), range patterns (`1..=5`), enum patterns (`Enum::Variant(x)`), `_` wildcard, and arm guards (`pattern if condition => ...`).
- User-defined enums with unit or single-payload variants.
- Built-in `Option` and `Result` enums (lowered to enums with `Some/None` and `Ok/Err` variants; payload uses `Any` in v1).
- Assignments (`=`) and compound assignments (`+=`, `-=`, `*=`, `/=`) for `i32`.

Example control flow:
```just
fn main() {
    let x = 3;
    if x == 0 {
        std::print("zero");
    } else if x < 3 {
        std::print("small");
    } else {
        std::print("big");
    }
    return;
}
```

Example `if let` / `while let`:
```just
fn main() {
    let mut value = Option::Some(3);
    if let Option::Some(x) = value {
        std::print(x);
    }
    while let Option::Some(x) = value {
        std::print(x);
        value = Option::None;
    }
    return;
}
```
