# Just (Rust-on-JVM) — Design Notes (Living Doc)

This document is intentionally iterative. It captures current design decisions, the rationale behind them, and open questions.

## One-Line Pitch

**Just** is a Rust-inspired systems language that targets **JVM bytecode**, enforces **ownership/borrowing** at compile time, and keeps **Just-owned program data off-heap** (no tracing GC for your data) while still enabling **pragmatic Java/Kotlin interop** (including frameworks like Spring).

## Non-Negotiable Clarification: “No GC”

On the JVM it is not possible to have *literally zero garbage collection* because the JVM itself is GC-managed (classes, metadata, runtime objects, interop objects).

What Just guarantees (goal for v1):

- **No GC for Just-owned program data**: user-visible aggregates (structs/enums/collections) live **off-heap** in owning types like `Box`, `Vec`, and `String`.
- **Minimal GC footprint** for runtime scaffolding and boundary objects: bounded number of JVM objects for runtime/interop, not per element.

## Goals (v1)

- Rust 2021–like ownership/borrowing, including NLL-style behavior and two-phase borrows (as close as feasible).
- “Zero-cost” abstractions in the Rust sense:
  - no hidden allocations
  - no runtime borrow checks in safe code
  - generics are monomorphized by default
- Deterministic memory management for Just-owned data via `Drop` on owning types; off-heap allocation with explicit frees.
- Interop with Java/Kotlin:
  - can call Java/Kotlin libraries
  - can be hosted by frameworks like Spring (as a shell)
- Emit JVM bytecode directly.

## Non-Goals (v1)

- Full Rust stdlib parity (IO/FS/Net/async etc.).
- “Everything is off-heap” including all Java/Kotlin framework objects (not realistic).
- Advanced borrow-checker engines (e.g., Polonius-level precision).
- Depending on Project Valhalla for correctness.

## Big Picture Architecture

- **Frontend**: lexer/parser (Rust-like surface), name resolution, typechecking.
- **Ownership/Borrowing**: MIR-like IR with lifetime/loan tracking; rejects aliasing violations.
- **Backend**: JVM bytecode emitter.
- **Runtime**: off-heap allocator + drop glue + panic/reporting; thin and boring by design.

## Interop Philosophy (Updated)

Interop is allowed and encouraged for adoption, but must not silently destroy the language’s safety/perf properties.

Key idea: **Shell/Core split**.

- “Shell” is JVM frameworks and glue code (Spring, Jackson, etc.) in GC-land.
- “Core” is Just-owned data + logic off-heap with borrow-checked semantics.
- Boundaries perform explicit conversion/marshalling.

### Why frameworks like Spring “feel like they remove the benefits”

Spring encourages:

- long-lived object graphs (GC)
- reflection/dynamic proxies
- pervasive aliasing through shared references

If a Just program models its primary domain state as Spring-managed objects, the “Just benefits” are limited.

The intended Just usage is:

- keep Spring components small and boundary-oriented
- convert inputs into off-heap domain representations
- run core logic off-heap
- convert outputs back into DTOs at the boundary

## Memory Model (Deep Dive)

### Two Worlds

Just separates runtime concerns into two categories:

1) **Just-owned off-heap memory** (primary)

- structs/enums/collections live off-heap
- lifetime ties to ownership (`Box`, `Vec`, `String`)
- drop is deterministic via `Drop`

2) **JVM GC objects** (interop)

- framework objects (Spring, Jackson), Java collections, exceptions, etc.
- cannot be “owned” by Just
- must not be stored inside off-heap Just structs in v1
- boundary-only types (conversion/marshalling required)

### Off-Heap Substrate (Recommended: Java FFM)

Just’s runtime will be built on the Java Foreign Function & Memory API (FFM):

- `Linker` for ABI-level calls into native libraries when needed
- `malloc/realloc/free` as the primary allocator for Just-owned data
- `MemorySegment`/`VarHandle` may be used internally for access, but are not user-visible

Target recommendation:

- **JDK 22+** (FFM finalized), or later.

### Allocation Model (User-Facing)

- Stack allocation by default.
- Heap allocation via `Box<T>`, `Vec<T>`, and `String`.
- `Drop` deterministically frees heap allocations.
- Borrowing ties to ownership, not an arena lifetime.

### Reference Representation (Runtime)

Just references compile to raw-addressable locations plus static layout info (no user-visible handles).

Loads/stores are generated as:

- `segment.get(layout, base + offset + fieldOffset)`
- `segment.set(layout, base + offset + fieldOffset, value)`

### Drop Glue

Just supports Rust-like deterministic destruction for Just-owned values:

- compiler inserts destructor calls on scope exit (normal and exceptional paths)
- for heap-allocated values (`Box`, `Vec`, `String`), `Drop` calls free their buffers

### Concurrency + Regions

V1 design:

- Regions are *not* thread-safe by default.
- “confined” regions are single-threaded (similar to `Arena.ofConfined()`).
- sharing across threads requires explicit APIs and trait bounds (`Send`/`Sync`-like).

## Borrow Checking (v1 Scope)

Borrow checker goals:

- Rust-like aliasing: either many `&T` or exactly one `&mut T`
- no use-after-move
- lifetime inference for locals and temporaries

Explicit design decisions (v1):

- No `&mut` to JVM GC objects.
- Passing pointers/references into Java/Kotlin or native code is `unsafe`.

## Ownership & Borrowing (Example-Level Semantics)

Just mirrors Rust-style ownership with compile-time enforcement.

**Ownership**

- Values are owned by default.
- Assignment or passing moves ownership (unless `Copy`-like).
- Use-after-move is a compile-time error.

**Borrowing**

- `&T` allows many shared borrows.
- `&mut T` is exclusive (no other shared or mutable borrows live).
- Borrows end at last use (NLL-style), not necessarily end of scope.

**Lifetimes**

- Inferred for locals.
- Explicit in signatures when needed.
- References cannot outlive the owner; heap allocations are freed on `Drop`.

**Interop**

- JVM GC references are never owned by Just.
- Only shared borrows of GC objects in v1.
- Passing Just references across interop boundaries is `unsafe`.

## Java/Kotlin Interop (v1)

### GC References Are Explicit

We still need a type-system notion of “GC-owned reference” for interop, even if we avoid the term “opaque”.

Candidate surface:

- `jvm::Ref<T>` for a GC-managed reference
- `jvm::StringRef`, `jvm::ListRef<T>`, etc. as aliases/wrappers for common platform types

Rules:

- GC refs are **not ownable** and have **no deterministic drop**
- GC refs cannot be embedded into off-heap structs in v1
- GC refs can be borrowed as shared (`&jvm::Ref<T>`), and passed across the boundary

### Exception Policy

V1 default:

- Java exceptions become `Result<T, jvm::ExceptionRef>` at boundary APIs.

## Native (C ABI) Interop (Optional in v1)

Using Java FFM `Linker`, Just can call native functions (libc, OS APIs, high-perf native libs).

Important boundaries:

- FFI is `unsafe` (foreign code can violate aliasing/lifetime rules).
- Passing arena references to native code requires proving the reference won’t outlive the arena.
- Callbacks from native into Just must not capture arena references unless explicitly pinned/leaked.

## Build & Packaging (v1)

- Cargo-like `just.toml` describing packages, dependencies, and build targets.
- Output: `.jar` runnable by `java -jar`.

Interop:

- Gradle/Maven integration is “nice to have”, not required for v1.

## Standard Library (v1)

- `core`: primitives, `Option`, `Result`, basic traits
- `alloc`: owning types (`Box`, `Vec`, `String`) and allocation helpers
- `collections`: off-heap owned `Vec`, `String`, `Map` (initially minimal)

Use Java libraries for:

- IO/FS/Net/HTTP (via interop)

## Example: Spring CRUD (Shell/Core Pattern)

Intent:

- Spring controller/service are JVM shell code
- domain validation/transforms happen in Just-owned data
- persistence can stay in JVM land initially

See `docs/examples/spring-crud.just` (TODO: add once compiler exists).

## Roadmap

1) Minimal compiler that can compile a tiny program to a `.jar` and call `System.out.println`.
2) Add off-heap allocator runtime (FFM-backed) and `String`/`Vec`/`Box`.
3) Implement borrow checker for locals + heap references.
4) Java interop for calling methods and constructing common platform types.
5) Minimal Spring interop:
   - annotation/metadata emission
   - generate JVM-friendly entry points

## Open Questions

- JDK baseline: **22+** (FFM final).
- “Release-fast” mode: allow eliding some FFM checks in safe code?
- How to represent trait objects on JVM bytecode (invokedynamic? interfaces?).
- How far v1 goes on `match`/enums lowering without exploding allocations.
