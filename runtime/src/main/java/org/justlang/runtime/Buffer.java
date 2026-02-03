package org.justlang.runtime;

public final class Buffer {
    private Allocation allocation;
    private long length;

    public Buffer(Allocation allocation, long length) {
        this.allocation = allocation;
        this.length = length;
    }

    public Allocation grow(long minCapacity, Layout layout, JustHeap heap) {
        long elementSize = layout.size();
        long currentCapacity = allocation.size() / elementSize;
        if (minCapacity <= currentCapacity) {
            return allocation;
        }

        long newCapacity = Math.max(minCapacity, currentCapacity * 2);
        long newSize = newCapacity * elementSize;
        Allocation grown = heap.realloc(allocation, Layout.of(newSize, layout.align()));
        this.allocation = grown;
        return grown;
    }

    public Allocation allocation() {
        return allocation;
    }

    public long length() {
        return length;
    }
}
