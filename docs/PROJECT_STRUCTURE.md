# Project Structure (Planned)

This document defines the target structure for the Just repository. The repo uses the multi-module layout below.

## Target Layout

```text
just/
  compiler/
    src/main/java/...
    src/test/java/...
  runtime/
    src/main/java/...
    src/test/java/...
  stdlib/
    src/main/java/...
    src/test/java/...
  cli/
    src/main/java/...
    src/test/java/...
  interop/
    src/main/java/...
    src/test/java/...
  examples/
  docs/
    DESIGN.md
    PROJECT_STRUCTURE.md
  gradle/
  settings.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
```

## Gradle Modules

- `compiler`: front-end (lexer/parser), type checker, borrow checker, MIR, JVM bytecode emitter.
- `runtime`: off-heap allocator, drop glue, panic/reporting, FFM wrappers.
- `stdlib`: `core`, `alloc`, `collections` (off-heap owned types).
- `cli`: `just` command-line driver and packaging logic.
- `interop`: JVM interop utilities and boundary types (`jvm::*Ref`).

## Entry Points

- Compiler API entry: `compiler` module public façade used by `cli`.
- CLI entry: `cli` module `main` (invokes compiler, produces `.jar`).
- Runtime entry: `runtime` module is linked by compiler output as needed.

## Examples

- `examples/`: small Just programs and interop samples (e.g., Spring CRUD shell).

## Tests

- Unit tests co-located per module (`src/test/java`).
- Integration tests under `cli` or `examples` once compilation pipeline exists.

## Notes

- Baseline JDK: 22+ (FFM finalized).
