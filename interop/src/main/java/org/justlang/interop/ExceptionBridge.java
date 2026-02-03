package org.justlang.interop;

public final class ExceptionBridge {
    public JvmExceptionRef fromThrowable(Throwable throwable) {
        return new JvmExceptionRef(throwable);
    }
}
