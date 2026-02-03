package org.justlang.interop;

public final class JvmStringRef extends JvmRef<String> {
    public JvmStringRef(String value) {
        super(value);
    }

    public String value() {
        return get();
    }
}
