# Runtime Module (`runtime/`)

## Purpose

Provides off-heap allocation, a high-level Just-owned heap API on top of low-level primitives, memory access helpers, drop glue support, and minimal runtime services required by compiled Just programs.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `RawAllocator` | Thin wrapper over FFM `malloc/realloc/free`. | `alloc(long, long): long`, `realloc(long, long, long): long`, `free(long): void` |
| `JustHeap` | High-level heap API for compiler-generated code. | `alloc(Layout): Allocation`, `realloc(Allocation, Layout): Allocation`, `free(Allocation): void`, `stats(): HeapStats` |
| `Allocation` | Describes an off-heap allocation. | `address(): long`, `size(): long`, `align(): int` |
| `Layout` | Size + alignment for allocation. | `of(size, align): Layout` |
| `Buffer` | Growable buffer abstraction used by `Vec`/`String`. | `grow(minCapacity): Allocation`, `asSlice(): Slice` |
| `FfmLinker` | Resolves libc symbols (`malloc`, `realloc`, `free`, `memcpy`). | `loadLibc(): void`, `mallocHandle(): MethodHandle` |
| `MemoryAccess` | Loads/stores primitives at raw addresses. | `getI32(long): int`, `setI64(long, long): void` |
| `DropGlue` | Dispatches drop routines for owned types. | `drop(long, int): void` |
| `Panic` | Panic reporting and unwinding integration. | `panic(String): void` |
| `RuntimeConfig` | Runtime settings (debug checks, allocator flags). | `fromEnv(): RuntimeConfig` |

## Data Flow

1. Compiler emits calls to `JustHeap.alloc` for `Box`, `Vec`, and `String` buffers.
2. `JustHeap` delegates raw allocation to `RawAllocator`.
3. `Buffer` manages growth via `JustHeap.realloc`.
4. `MemoryAccess` performs typed reads/writes at offsets inside allocations.
5. On scope exit, compiler emits `DropGlue.drop` for owned values.
6. `JustHeap.free` releases memory deterministically.
7. `Panic` is invoked for unrecoverable failures.

## Notes

- FFM is used internally, not exposed to users.
- No GC for Just-owned memory; JVM GC only covers runtime scaffolding.
