package org.justlang.runtime;

public final class Allocation {
    private final long address;
    private final long size;
    private final int align;

    public Allocation(long address, long size, int align) {
        this.address = address;
        this.size = size;
        this.align = align;
    }

    public long address() {
        return address;
    }

    public long size() {
        return size;
    }

    public int align() {
        return align;
    }
}
