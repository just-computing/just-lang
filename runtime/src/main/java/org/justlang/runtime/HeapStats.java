package org.justlang.runtime;

public final class HeapStats {
    private final long allocatedBytes;
    private final long allocationCount;

    public HeapStats(long allocatedBytes, long allocationCount) {
        this.allocatedBytes = allocatedBytes;
        this.allocationCount = allocationCount;
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    public long allocationCount() {
        return allocationCount;
    }
}
