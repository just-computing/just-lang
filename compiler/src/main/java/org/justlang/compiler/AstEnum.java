package org.justlang.compiler;

import java.util.List;

public final class AstEnum implements AstItem {
    private final String name;
    private final List<AstEnumVariant> variants;

    public AstEnum(String name, List<AstEnumVariant> variants) {
        this.name = name;
        this.variants = variants;
    }

    public String name() {
        return name;
    }

    public List<AstEnumVariant> variants() {
        return variants;
    }
}
