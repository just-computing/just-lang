package org.justlang.compiler;

import java.util.HashMap;
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

    public record FunctionSig(String name, TypeId returnType, int paramCount) {}
}
