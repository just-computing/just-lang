# Just Language

`just` is a Rust-inspired language targeting the JVM, with a Java-based compiler and CLI.

## Prerequisites

- JDK 22+ for running the installed `just` binary.
- Gradle wrapper (`./gradlew`) is included in this repository.

## Build the CLI binary

```bash
./gradlew :cli:installDist
```

The executable is generated at:

```bash
./cli/build/install/just/bin/just
```

If your system default `java` is older than 22, use the Gradle launcher path:

```bash
./gradlew :cli:run --args='examples/hello.just'
```

## CLI usage

Compile directly:

```bash
./cli/build/install/just/bin/just examples/hello.just
```

Compile with explicit command:

```bash
./cli/build/install/just/bin/just build examples/hello.just
```

Compile and run:

```bash
./cli/build/install/just/bin/just run examples/hello.just
```

Type-check only:

```bash
./cli/build/install/just/bin/just check examples/hello.just
```

## Modules and imports

Use explicit file imports at the top level:

```just
import "app.just";
import "feature/math.just";
```

Or module declarations:

```just
mod app;
mod feature::math;
```

Public module API and symbol imports:

```just
use app::app_greeting;

pub fn app_greeting() -> String {
    return "hello";
}
```

Import paths are resolved relative to the importing source file.
The compiler loads imports transitively and reports errors for missing files or import cycles.
Cross-file unqualified calls require `use module::symbol;` and private (`fn`) module functions are not callable from other modules.
In this iteration, visibility enforcement is implemented for functions (`pub fn`).

Dependency imports (from `just.toml` aliases) use `@alias/...`:

```just
import "@shared/math.just";
use math::double_value;
```

Project commands:

```bash
./cli/build/install/just/bin/just jargo new my-app
./cli/build/install/just/bin/just jargo build
./cli/build/install/just/bin/just jargo run
```

`jargo new` scaffolds:

- `src/main.just` as entrypoint
- `src/app.just` as an imported module
- `just.toml` with `main = "src/main.just"`

`just.toml` controls build entrypoint:

```toml
name = "my-app"
main = "src/main.just"

[dependencies]
shared = { path = "../shared-lib" }
```

## Merge sort sample

A merge-sort style sample is available at:

```bash
examples/merge_sort.just
```

Compile it:

```bash
./cli/build/install/just/bin/just examples/merge_sort.just
```
