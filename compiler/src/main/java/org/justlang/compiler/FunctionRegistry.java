package org.justlang.compiler;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FunctionRegistry {
    private final Map<String, FunctionSig> functions = new HashMap<>();

    public void register(String name, FunctionSig sig) {
        functions.put(name, sig);
    }

    public FunctionSig find(String name) {
        return functions.get(name);
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    public record FunctionSig(
        String name,
        TypeId returnType,
        List<TypeId> paramTypes,
        String moduleName,
        Path sourcePath,
        boolean publicItem
    ) {
        public int paramCount() {
            return paramTypes.size();
        }
    }
}
