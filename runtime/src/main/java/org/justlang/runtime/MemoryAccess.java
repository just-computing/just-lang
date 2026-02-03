package org.justlang.runtime;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class MemoryAccess {
    public int getI32(long address) {
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(Integer.BYTES);
        return segment.get(JAVA_INT, 0);
    }

    public long getI64(long address) {
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(Long.BYTES);
        return segment.get(JAVA_LONG, 0);
    }

    public void setI32(long address, int value) {
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(Integer.BYTES);
        segment.set(JAVA_INT, 0, value);
    }

    public void setI64(long address, long value) {
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(Long.BYTES);
        segment.set(JAVA_LONG, 0, value);
    }
}
