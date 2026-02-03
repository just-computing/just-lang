package org.justlang.compiler;

public final class ClassFile {
    private final String internalName;
    private final byte[] bytes;

    public ClassFile(String internalName, byte[] bytes) {
        this.internalName = internalName;
        this.bytes = bytes;
    }

    public String internalName() {
        return internalName;
    }

    public byte[] bytes() {
        return bytes;
    }
}
