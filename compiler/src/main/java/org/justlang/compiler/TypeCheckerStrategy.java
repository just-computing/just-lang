package org.justlang.compiler;

public interface TypeCheckerStrategy {
    TypeResult typeCheck(AstModule module);
}
