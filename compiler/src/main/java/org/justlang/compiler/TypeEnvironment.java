package org.justlang.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypeEnvironment {
    private final Map<String, TypeId> locals = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    public void define(String name, TypeId type) {
        locals.put(name, type);
    }

    public TypeEnvironment fork() {
        TypeEnvironment forked = new TypeEnvironment();
        forked.locals.putAll(this.locals);
        return forked;
    }

    public TypeId lookup(String name) {
        return locals.get(name);
    }

    public void addError(String message) {
        errors.add(message);
    }

    public List<String> errors() {
        return errors;
    }
}
