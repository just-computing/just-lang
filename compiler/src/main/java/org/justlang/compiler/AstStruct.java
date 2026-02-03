package org.justlang.compiler;

import java.util.List;

public final class AstStruct implements AstItem {
    private final String name;
    private final List<AstField> fields;

    public AstStruct(String name, List<AstField> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String name() {
        return name;
    }

    public List<AstField> fields() {
        return fields;
    }
}
