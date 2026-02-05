package org.justlang.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypeEnvironment {
    private final Map<String, Binding> locals = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void define(String name, TypeId type, boolean mutable) {
        locals.put(name, new Binding(type, mutable, false));
    }

    public TypeEnvironment fork() {
        TypeEnvironment forked = new TypeEnvironment();
        forked.locals.putAll(this.locals);
        return forked;
    }

    public Binding lookup(String name) {
        return locals.get(name);
    }

    public void markMoved(String name) {
        Binding binding = locals.get(name);
        if (binding == null) {
            return;
        }
        locals.put(name, new Binding(binding.type(), binding.mutable(), true));
    }

    public void clearMoved(String name) {
        Binding binding = locals.get(name);
        if (binding == null) {
            return;
        }
        locals.put(name, new Binding(binding.type(), binding.mutable(), false));
    }

    public void addError(String message) {
        errors.add(message);
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public List<String> errors() {
        return errors;
    }

    public List<String> warnings() {
        return warnings;
    }

    public record Binding(TypeId type, boolean mutable, boolean moved) {}
}
