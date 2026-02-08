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

Project commands:

```bash
./cli/build/install/just/bin/just jargo new my-app
./cli/build/install/just/bin/just jargo build
./cli/build/install/just/bin/just jargo run
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
