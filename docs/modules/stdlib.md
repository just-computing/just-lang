# Stdlib Module (`stdlib/`)

## Purpose

Defines the core language types and collections used by Just programs, implemented on top of the runtime’s off-heap allocator.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `Option<T>` | Optional value type. | `isSome(): boolean`, `unwrap(): T` |
| `Result<T,E>` | Error-handling type. | `isOk(): boolean`, `unwrap(): T`, `unwrapErr(): E` |
| `Box<T>` | Owns a single heap allocation. | `new(T): Box<T>`, `get(): &T`, `getMut(): &mut T` |
| `Vec<T>` | Growable off-heap buffer. | `new(): Vec<T>`, `push(T): void`, `len(): int` |
| `String` | UTF-8/UTF-16 off-heap string. | `fromJvm(StringRef): String`, `toJvm(): StringRef` |
| `Map<K,V>` | Key/value collection (v1 minimal). | `get(K): Option<V>`, `insert(K, V): void` |
| `Slice<T>` | View into contiguous elements. | `len(): int`, `get(int): &T` |

## Data Flow

1. `Box/Vec/String` request memory from `runtime::OffHeapAllocator`.
2. `Vec` grows via `realloc` when capacity is exceeded.
3. `String` boundary conversions copy data between JVM strings and off-heap buffers.
4. `Drop` implementations call back into runtime to free allocations.

## Notes

- `core` (Option/Result/traits), `alloc` (Box/Vec/String), and `collections` (Map) are built on the same off-heap allocator.
- GC objects are only used for boundary conversions (e.g., `StringRef`), not stored in off-heap structures.

