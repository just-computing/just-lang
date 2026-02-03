package org.justlang.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructRegistry {
    private final Map<String, StructDef> structs = new HashMap<>();

    public void register(AstStruct struct) {
        structs.put(struct.name(), new StructDef(struct.name(), struct.fields()));
    }

    public StructDef find(String name) {
        return structs.get(name);
    }

    public record StructDef(String name, List<AstField> fields) {
        public AstField field(String fieldName) {
            for (AstField field : fields) {
                if (field.name().equals(fieldName)) {
                    return field;
                }
            }
            return null;
        }
    }
}
