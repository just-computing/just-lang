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

    public void setType(String name, TypeId type) {
        Binding binding = locals.get(name);
        if (binding == null) {
            return;
        }
        locals.put(name, new Binding(type, binding.mutable(), binding.moved()));
    }

    public void refineType(String name, TypeId constraint) {
        Binding binding = locals.get(name);
        if (binding == null) {
            return;
        }
        TypeId refined = TypeUnifier.refine(binding.type(), constraint);
        if (!refined.equals(binding.type())) {
            locals.put(name, new Binding(refined, binding.mutable(), binding.moved()));
        }
    }

    public void joinType(String name, TypeId other) {
        Binding binding = locals.get(name);
        if (binding == null) {
            return;
        }
        TypeId joined = TypeUnifier.join(binding.type(), other);
        if (!joined.equals(binding.type())) {
            locals.put(name, new Binding(joined, binding.mutable(), binding.moved()));
        }
    }

    public void mergeMovedFrom(TypeEnvironment other) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding branch = other.locals.get(name);
            boolean moved = current.moved() || (branch != null && branch.moved());
            locals.put(name, new Binding(current.type(), current.mutable(), moved));
        }
    }

    public void mergeTypesFrom(TypeEnvironment other) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding branch = other.locals.get(name);
            if (branch == null) {
                continue;
            }
            TypeId type = TypeUnifier.join(current.type(), branch.type());
            locals.put(name, new Binding(type, current.mutable(), current.moved()));
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

    public void adoptTypesFrom(TypeEnvironment other) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding branch = other.locals.get(name);
            if (branch == null) {
                continue;
            }
            locals.put(name, new Binding(branch.type(), current.mutable(), current.moved()));
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

    public void joinTypesFrom(TypeEnvironment left, TypeEnvironment right) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding leftBinding = left.locals.get(name);
            Binding rightBinding = right.locals.get(name);
            TypeId leftType = leftBinding != null ? leftBinding.type() : current.type();
            TypeId rightType = rightBinding != null ? rightBinding.type() : current.type();
            TypeId type = TypeUnifier.join(leftType, rightType);
            locals.put(name, new Binding(type, current.mutable(), current.moved()));
        }
    }

    public boolean joinTypesFromStrict(TypeEnvironment left, TypeEnvironment right, java.util.function.Consumer<String> reportError) {
        boolean ok = true;
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            Binding leftBinding = left.locals.get(name);
            Binding rightBinding = right.locals.get(name);
            TypeId leftType = leftBinding != null ? leftBinding.type() : current.type();
            TypeId rightType = rightBinding != null ? rightBinding.type() : current.type();
            TypeId joined = TypeUnifier.tryJoin(leftType, rightType);
            if (joined == null) {
                reportError.accept("Type mismatch across control flow for " + name + ": " + leftType + " vs " + rightType);
                joined = TypeId.UNKNOWN;
                ok = false;
            }
            locals.put(name, new Binding(joined, current.mutable(), current.moved()));
        }
        return ok;
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

    public void joinTypesFromAll(List<TypeEnvironment> branches, boolean includeCurrentState) {
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            TypeId type = includeCurrentState ? current.type() : null;
            for (TypeEnvironment branch : branches) {
                Binding branchBinding = branch.locals.get(name);
                if (branchBinding == null) {
                    continue;
                }
                type = type == null ? branchBinding.type() : TypeUnifier.join(type, branchBinding.type());
            }
            if (type == null) {
                type = current.type();
            }
            locals.put(name, new Binding(type, current.mutable(), current.moved()));
        }
    }

    public boolean joinTypesFromAllStrict(
        List<TypeEnvironment> branches,
        boolean includeCurrentState,
        java.util.function.Consumer<String> reportError
    ) {
        boolean ok = true;
        for (String name : new ArrayList<>(locals.keySet())) {
            Binding current = locals.get(name);
            TypeId type = includeCurrentState ? current.type() : null;
            for (TypeEnvironment branch : branches) {
                Binding branchBinding = branch.locals.get(name);
                if (branchBinding == null) {
                    continue;
                }
                if (type == null) {
                    type = branchBinding.type();
                    continue;
                }
                TypeId joined = TypeUnifier.tryJoin(type, branchBinding.type());
                if (joined == null) {
                    reportError.accept(
                        "Type mismatch across control flow for " + name + ": " + type + " vs " + branchBinding.type()
                    );
                    type = TypeId.UNKNOWN;
                    ok = false;
                    break;
                }
                type = joined;
            }
            if (type == null) {
                type = current.type();
            }
            locals.put(name, new Binding(type, current.mutable(), current.moved()));
        }
        return ok;
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
