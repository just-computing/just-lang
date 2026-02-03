# Compiler Module (`compiler/`)

## Purpose

Parses Just source, performs name/type/borrow analysis, lowers to IR, monomorphizes generics, and emits JVM bytecode packaged into `.jar` outputs.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `JustCompiler` | End-to-end compilation orchestrator. | `compile(CompileRequest): CompileResult` |
| `CompilerConfig` | Compiler settings, target, and feature flags. | `fromToml(Path): CompilerConfig` |
| `SourceLoader` | Reads source files and builds module graph. | `load(Project): List<SourceFile>` |
| `Lexer` | Tokenizes source text. | `lex(SourceFile): List<Token>` |
| `Parser` | Builds AST from tokens. | `parse(List<Token>): AstModule` |
| `NameResolver` | Resolves symbols to bindings. | `resolve(AstModule): HirModule` |
| `TypeChecker` | Infers and checks types. | `typeCheck(HirModule): TypedModule` |
| `BorrowChecker` | Enforces ownership/borrowing rules. | `check(TypedModule): BorrowResult` |
| `MirBuilder` | Lowers typed HIR to MIR. | `lower(TypedModule): MirModule` |
| `Monomorphizer` | Specializes generics. | `specialize(MirModule): MirModule` |
| `Codegen` | Emits JVM bytecode. | `emit(MirModule): List<ClassFile>` |
| `JarEmitter` | Writes `.jar` with manifest and classes. | `writeJar(List<ClassFile>, Path): void` |
| `Diagnostics` | Collects and formats errors. | `report(Diagnostic): void` |

## Data Flow

1. `SourceLoader` reads project sources and builds the module graph.
2. `Lexer` produces tokens from each source file.
3. `Parser` builds AST (`AstModule` and items).
4. `NameResolver` produces HIR with resolved bindings.
5. `TypeChecker` infers/checks types and produces `TypedModule`.
6. `BorrowChecker` validates ownership and lifetimes.
7. `MirBuilder` lowers to MIR.
8. `Monomorphizer` specializes generics.
9. `Codegen` emits JVM class files.
10. `JarEmitter` packages class files into a runnable `.jar`.

## Outputs

- `.jar` file with generated JVM classes
- `Diagnostics` for errors/warnings

