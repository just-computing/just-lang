package org.justlang.compiler;

import java.util.List;

public final class CompileResult {
    private final boolean success;
    private final List<Diagnostic> diagnostics;

    public CompileResult(boolean success, List<Diagnostic> diagnostics) {
        this.success = success;
        this.diagnostics = diagnostics;
    }

    public boolean success() {
        return success;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
