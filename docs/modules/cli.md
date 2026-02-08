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
| `ProjectLoader` | Resolves the input path used for a compile/check operation. | `load(Path): ProjectConfig` |
| `ProjectManifest` | Reads `just.toml` entrypoint and dependency aliases. | `load(Path): ProjectManifest`, `dependencyRoots(Path): Map<String, Path>` |
| `CompilerService` | Invokes `compiler::JustCompiler`. | `build(ProjectConfig): CompileResult` |
| `JarRunner` | Runs produced `.jar`. | `runJar(Path): int` |
| `JargoNewCommand` | Creates a multi-file app template (`src/main.just` + `src/app.just`). | `run(): int` |

## Data Flow

1. `JustCli` parses args via `ArgsParser`.
2. For project directories, `ProjectManifest` resolves `main` from `just.toml`.
3. `ProjectLoader` converts file/directory input into `ProjectConfig` (entrypoint + project root + dependency roots).
4. `CompilerService` invokes the compiler to produce class files and a `.jar`.
5. `RunCommand`/`JargoRunCommand` executes the generated `.jar` when requested.
