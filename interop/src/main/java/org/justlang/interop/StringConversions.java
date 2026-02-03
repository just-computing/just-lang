package org.justlang.interop;

import org.justlang.stdlib.String;

public final class StringConversions {
    public String toJust(JvmStringRef value) {
        return String.fromJvm(value.value());
    }

    public JvmStringRef toJvm(String value) {
        return new JvmStringRef(value.toJvm());
    }
}
