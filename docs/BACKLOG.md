# Backlog: Compile Regular Rust Code

This backlog defines the work required for `just` to compile real Rust projects (Rust 2021 crates), not only `.just` sources.

## Target Outcome

- Given a standard Rust crate with `Cargo.toml` + `src/*.rs`, `just` can build and run it on JVM without source rewrites.
- Compatibility target: start with a clearly documented Rust subset, then expand until common real-world crates build.

## Exit Criteria

- `just` builds at least one non-trivial multi-module Rust application and one library crate with tests.
- `just` can resolve dependencies from `crates.io`/git/path and handle workspaces.
- Macro pipeline (declarative + proc macros) works for common ecosystem crates.
- Compatibility suite tracks pass/fail by feature and crate.

## Phase 0: Foundation and Guardrails

- [x] P0.1 Expand source discovery to include `.rs` files in project scans.
- [x] P0.2 Resolve `mod ...;` declarations for `.rs`, `.just`, `mod.rs`, and `mod.just`.
- [x] P0.3 Accept Rust outer attributes syntax tokens (`#[]`) at parser entry points.
- [x] P0.4 Accept macro-style calls (`name!(...)`) for bootstrap compatibility (`println!` path).
- [ ] P0.5 Add compatibility status command (`just rust doctor`) with explicit unsupported-feature report.
- [ ] P0.6 Establish `rust-compat` fixtures and CI gate with expected failures tracking.

## Phase 1: Rust Project Ingestion (Cargo-Compatible Front Door)

- [ ] P1.1 Parse `Cargo.toml` (package, lib/bin targets, features, editions, profiles).
- [ ] P1.2 Build crate graph (workspace members, path/git/registry deps).
- [ ] P1.3 Implement lockfile semantics (`Cargo.lock`) for deterministic resolution.
- [ ] P1.4 Implement command surface parity for core flows (`build`, `check`, `run`, `test`).
- [ ] P1.5 Support target selection (`--bin`, `--lib`, `--example`, `--test`, `--bench`).

## Phase 2: Lexer/Parser Parity (Rust Surface Syntax)

- [ ] P2.1 Complete token model: lifetimes, char/byte/byte-string/raw string literals, doc comments.
- [ ] P2.2 Parse items: `trait`, `impl`, `type`, `const`, `static`, visibility forms (`pub(crate)` etc.).
- [ ] P2.3 Parse generics, bounds, `where` clauses, associated types/constants.
- [ ] P2.4 Parse patterns, match ergonomics, destructuring, ranges, slice/rest patterns.
- [ ] P2.5 Parse operators including `?`, `as`, method-call chains, turbofish, UFCS.
- [ ] P2.6 Parse full module/use syntax (`crate::`, `self::`, `super::`, grouped imports, globs).
- [ ] P2.7 Parse async syntax (`async fn`, `.await`) and unsafe blocks/items.

## Phase 3: Name Resolution and Module System

- [ ] P3.1 Implement Rust name resolution rules across module tree and extern crates.
- [ ] P3.2 Implement macro namespace resolution (`macro_rules!`, proc-macro exports/imports).
- [ ] P3.3 Implement visibility/privacy parity and edition-specific path rules.
- [ ] P3.4 Implement coherent symbol tables for item/value/type/macro namespaces.

## Phase 4: Type System and Traits

- [ ] P4.1 Extend primitive and standard nominal types (`u*`, `i*`, `f*`, `char`, tuples, arrays, slices).
- [ ] P4.2 Implement trait solving baseline (bounds checking, method lookup, associated items).
- [ ] P4.3 Implement generics + monomorphization strategy with trait constraints.
- [ ] P4.4 Implement coercions/subtyping rules needed by common crates.
- [ ] P4.5 Implement error model with rustc-like diagnostics quality and spans.

## Phase 5: Borrow Checking and MIR-Level Semantics

- [ ] P5.1 Lower Rust AST to MIR-like IR with ownership/move semantics.
- [ ] P5.2 Implement borrow checker parity for core patterns (NLL-inspired baseline).
- [ ] P5.3 Implement drop elaboration and deterministic destruction model alignment.
- [ ] P5.4 Implement closure capture semantics and function traits baseline.

## Phase 6: Runtime and Std/Core Mapping

- [ ] P6.1 Define `core`/`alloc` compatibility layer mapping to JVM/runtime primitives.
- [ ] P6.2 Provide required lang items and panic strategy (`panic=abort` first).
- [ ] P6.3 Implement ABI-safe representation for enums/structs/tuples and fat pointers.
- [ ] P6.4 Add FFI/interop boundary rules for Rust/JVM crossing.

## Phase 7: Backend and Codegen Completeness

- [ ] P7.1 Extend JVM backend for full control flow/data layout required by Rust subset.
- [ ] P7.2 Implement trait method dispatch strategy (static and dynamic dispatch).
- [ ] P7.3 Implement async lowering/runtime strategy (`Future` model on JVM).
- [ ] P7.4 Implement debuginfo/symbol mapping for diagnostics and stack traces.

## Phase 8: Macro, Build Script, and Toolchain Compatibility

- [ ] P8.1 Support declarative macros (`macro_rules!`) expansion pipeline.
- [ ] P8.2 Support proc-macro crates execution model.
- [ ] P8.3 Support `build.rs` execution contracts and generated code integration.
- [ ] P8.4 Support environment contract expected by common crates (`CARGO_*`, target vars).

## Phase 9: Ecosystem and Quality Gates

- [ ] P9.1 Curate compatibility corpus (simple crates -> medium -> framework-heavy).
- [ ] P9.2 Add regression dashboard: feature pass-rate, crate pass-rate, compile-time trends.
- [ ] P9.3 Harden performance (incremental builds, caching, parallel crate compilation).
- [ ] P9.4 Write migration guide and explicit unsupported features matrix.

## Current Sprint (Started)

- [x] S1.1 `.rs` discovery + `mod` resolution improvements.
- [x] S1.2 Parser bootstrap for Rust attributes and `println!` macro-call shape.
- [ ] S1.3 Introduce dedicated Rust compatibility fixtures and CI job.
- [ ] S1.4 Implement `?` operator semantics in type checker + codegen.
- [ ] S1.5 Add first end-to-end crate target: multi-module no-dependency Rust app.
