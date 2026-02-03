# Interop Module (`interop/`)

## Purpose

Defines the Java/Kotlin interop boundary types and conversion helpers. Keeps GC objects at the boundary and prevents embedding them into off-heap structures.

## Key Classes (API Sketch)

| Class | Responsibility | Key Methods |
| --- | --- | --- |
| `JvmRef<T>` | GC-managed reference wrapper. | `get(): T` |
| `JvmStringRef` | JVM string wrapper. | `value(): String` |
| `JvmListRef<T>` | JVM list wrapper. | `size(): int`, `get(int): T` |
| `StringConversions` | Boundary conversions for strings. | `toJust(JvmStringRef): String`, `toJvm(String): JvmStringRef` |
| `ArrayConversions` | Convert arrays between JVM and Just. | `toJust(JvmArrayRef): Vec<T>` |
| `ExceptionBridge` | Converts JVM exceptions to `Result`. | `fromThrowable(Throwable): JvmExceptionRef` |

## Data Flow

1. Boundary code receives `Jvm*Ref` values from JVM frameworks (e.g., Spring/Jackson).
2. `*Conversions` copy data into off-heap `String`/`Vec` for core logic.
3. Core logic returns Just-owned values.
4. Boundary code converts outputs back into JVM objects for frameworks.

## Rules (Enforced by Compiler)

- GC refs are boundary-only and cannot be stored inside off-heap structs.
- No `&mut` to GC refs in v1.
- Passing Just references across the JVM/native boundary is `unsafe`.

