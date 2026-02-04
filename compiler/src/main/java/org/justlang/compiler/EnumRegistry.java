package org.justlang.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnumRegistry {
    private final Map<String, EnumDef> enums = new HashMap<>();

    public void register(AstEnum enumDef) {
        enums.put(enumDef.name(), new EnumDef(enumDef.name(), enumDef.variants()));
    }

    public EnumDef find(String name) {
        return enums.get(name);
    }

    public boolean contains(String name) {
        return enums.containsKey(name);
    }

    public record EnumDef(String name, List<AstEnumVariant> variants) {
        public AstEnumVariant variant(String variantName) {
            for (AstEnumVariant variant : variants) {
                if (variant.name().equals(variantName)) {
                    return variant;
                }
            }
            return null;
        }
    }
}
