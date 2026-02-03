package org.justlang.runtime;

public final class Layout {
    private final long size;
    private final int align;

    private Layout(long size, int align) {
        this.size = size;
        this.align = align;
    }

    public static Layout of(long size, int align) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }
        if (align <= 0 || (align & (align - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be power of two: " + align);
        }
        return new Layout(size, align);
    }

    public long size() {
        return size;
    }

    public int align() {
        return align;
    }
}
