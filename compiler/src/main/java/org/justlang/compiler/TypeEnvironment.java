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

    public void mergeMovedFrom(TypeEnvironment other) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding branch = other.locals.get(name);
            boolean moved = current.moved() || (branch != null && branch.moved());
            locals.put(name, new Binding(current.type(), current.mutable(), moved));
        }
    }

    public void adoptMovedFrom(TypeEnvironment other) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding branch = other.locals.get(name);
            if (branch == null) {
                continue;
            }
            locals.put(name, new Binding(current.type(), current.mutable(), branch.moved()));
        }
    }

    public void joinMovedFrom(TypeEnvironment left, TypeEnvironment right) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding leftBinding = left.locals.get(name);
            Binding rightBinding = right.locals.get(name);
            boolean moved = (leftBinding != null && leftBinding.moved())
                || (rightBinding != null && rightBinding.moved());
            locals.put(name, new Binding(current.type(), current.mutable(), moved));
        }
    }

    public void joinMovedFromAll(List<TypeEnvironment> branches, boolean includeCurrentState) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            boolean moved = includeCurrentState && current.moved();
            for (TypeEnvironment branch : branches) {
                Binding branchBinding = branch.locals.get(name);
                if (branchBinding != null && branchBinding.moved()) {
                    moved = true;
                    break;
                }
            }
            locals.put(name, new Binding(current.type(), current.mutable(), moved));
        }
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
