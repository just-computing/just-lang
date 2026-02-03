package org.justlang.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Diagnostics {
    private final List<Diagnostic> items = new ArrayList<>();

    public void report(Diagnostic diagnostic) {
        items.add(diagnostic);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(items);
    }
}
