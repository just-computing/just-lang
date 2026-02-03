# CLI Module (`cli/`)

## Purpose

Provides the `just` command-line interface: project discovery, build orchestration, and `.jar` output packaging.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `JustCli` | CLI entry point. | `main(String[]): void` |
| `ArgsParser` | Parses CLI arguments into commands. | `parse(String[]): Command` |
| `Command` | Base interface for CLI commands. | `run(): int` |
| `BuildCommand` | Compile project to `.jar`. | `run(): int` |
| `RunCommand` | Build and execute `.jar`. | `run(): int` |
| `CheckCommand` | Type/borrow-check without emitting bytecode. | `run(): int` |
| `ProjectLoader` | Reads `just.toml` and resolves deps. | `load(Path): ProjectConfig` |
| `CompilerService` | Invokes `compiler::JustCompiler`. | `build(ProjectConfig): CompileResult` |
| `JarRunner` | Runs produced `.jar`. | `runJar(Path): int` |

## Data Flow

1. `JustCli` parses args via `ArgsParser`.
2. `ProjectLoader` reads `just.toml` and resolves the module graph.
3. `CompilerService` invokes the compiler to produce class files and a `.jar`.
4. `RunCommand` executes the `.jar` when requested.

