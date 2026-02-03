package org.justlang.runtime;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

public final class RawAllocator {
    private final MethodHandle malloc;
    private final MethodHandle realloc;
    private final MethodHandle free;

    public RawAllocator() {
        this(new FfmLinker());
    }

    public RawAllocator(FfmLinker linker) {
        linker.loadLibc();
        this.malloc = linker.mallocHandle();
        this.realloc = linker.reallocHandle();
        this.free = linker.freeHandle();
    }

    public long alloc(long size, long align) {
        validateAlign(align);
        try {
            MemorySegment segment = (MemorySegment) malloc.invokeExact(size);
            long address = segment.address();
            if (address == 0) {
                throw new OutOfMemoryError("malloc returned null");
            }
            return address;
        } catch (Throwable error) {
            throw new RuntimeException("malloc failed", error);
        }
    }

    public long realloc(long address, long newSize, long align) {
        validateAlign(align);
        try {
            MemorySegment ptr = MemorySegment.ofAddress(address);
            MemorySegment segment = (MemorySegment) realloc.invokeExact(ptr, newSize);
            long newAddress = segment.address();
            if (newAddress == 0) {
                throw new OutOfMemoryError("realloc returned null");
            }
            return newAddress;
        } catch (Throwable error) {
            throw new RuntimeException("realloc failed", error);
        }
    }

    public void free(long address) {
        if (address == 0) {
            return;
        }
        try {
            MemorySegment ptr = MemorySegment.ofAddress(address);
            free.invokeExact(ptr);
        } catch (Throwable error) {
            throw new RuntimeException("free failed", error);
        }
    }

    private static void validateAlign(long align) {
        if (align <= 0 || (align & (align - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a power of two: " + align);
        }
    }
}
