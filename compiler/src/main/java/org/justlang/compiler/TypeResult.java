package org.justlang.compiler;

public final class TypeResult {
    private final boolean success;
    private final TypeEnvironment environment;

    public TypeResult(boolean success, TypeEnvironment environment) {
        this.success = success;
        this.environment = environment;
    }

    public boolean success() {
        return success;
    }

    public TypeEnvironment environment() {
        return environment;
    }
}
