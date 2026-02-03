package org.justlang.runtime;

public final class JustHeap {
    private final RawAllocator allocator;
    private final java.util.concurrent.atomic.AtomicLong allocatedBytes = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong allocationCount = new java.util.concurrent.atomic.AtomicLong();

    public JustHeap(RawAllocator allocator) {
        this.allocator = allocator;
    }

    public Allocation alloc(Layout layout) {
        long address = allocator.alloc(layout.size(), layout.align());
        allocatedBytes.addAndGet(layout.size());
        allocationCount.incrementAndGet();
        return new Allocation(address, layout.size(), layout.align());
    }

    public Allocation realloc(Allocation allocation, Layout layout) {
        long newAddress = allocator.realloc(allocation.address(), layout.size(), layout.align());
        long delta = layout.size() - allocation.size();
        if (delta != 0) {
            allocatedBytes.addAndGet(delta);
        }
        return new Allocation(newAddress, layout.size(), layout.align());
    }

    public void free(Allocation allocation) {
        allocator.free(allocation.address());
        allocatedBytes.addAndGet(-allocation.size());
        allocationCount.decrementAndGet();
    }

    public HeapStats stats() {
        return new HeapStats(allocatedBytes.get(), allocationCount.get());
    }

    public RawAllocator rawAllocator() {
        return allocator;
    }
}
