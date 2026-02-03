package org.justlang.compiler;

import java.util.List;

public final class AstModule {
    private final List<AstItem> items;

    public AstModule(List<AstItem> items) {
        this.items = items;
    }

    public List<AstItem> items() {
        return items;
    }
}
