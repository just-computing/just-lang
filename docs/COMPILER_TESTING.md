# Compiler Testing Guide

## E2E Fixture Tests

The compiler now has data-driven end-to-end tests in `compiler/src/test/resources/fixtures`.

Each fixture is one folder with:

- `main.just`: input program
- `expect.properties`: expected behavior
- `diagnostics.txt` (optional): expected compiler diagnostics
- `stdout.txt` (optional): expected runtime stdout (`run` fixtures)
- `stderr.txt` (optional): expected runtime stderr (`run` fixtures)

Example layout:

```text
compiler/src/test/resources/fixtures/
  run-option-match/
    expect.properties
    main.just
    stdout.txt
```

`expect.properties` keys:

- `command`: `check`, `build`, or `run`
- `success`: `true` or `false` (expected compile result)
- `diagnosticsMatch` (optional): `contains` (default) or `exact`

## How It Executes

`compiler/src/test/java/org/justlang/compiler/CompilerFixtureTest.java` discovers all fixture directories and creates one dynamic JUnit test per fixture.

`compiler/src/test/java/org/justlang/compiler/CompilerFixtureKit.java` executes each fixture:

1. Builds `CompileRequest` from `command`
2. Runs `JustCompiler.compile(...)`
3. Verifies compile success/failure
4. Verifies diagnostics against `diagnostics.txt` when provided
5. For `build` and `run`, verifies the output `.jar` exists and is non-empty
6. For `run`, executes `java -jar` and verifies `stdout` / `stderr`

## Commands

Run only fixture tests:

```bash
./gradlew :compiler:test --tests org.justlang.compiler.CompilerFixtureTest
```

Run all compiler checks:

```bash
./gradlew :compiler:check
```

## Adding a New Fixture

1. Create a new folder under `compiler/src/test/resources/fixtures/<name>/`
2. Add `main.just`
3. Add `expect.properties`
4. Add `diagnostics.txt`, `stdout.txt`, `stderr.txt` as needed
5. Run the fixture test command above
