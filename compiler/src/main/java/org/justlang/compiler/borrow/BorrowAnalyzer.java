package org.justlang.compiler.borrow;

/**
 * High-level borrow checking API consumed by the type checker.
 *
 * <p>This interface exposes intent-level operations ("can we move/assign/borrow this binding?")
 * and hides tracker internals so alternative borrow-checking strategies can be introduced later.
 */
public interface BorrowAnalyzer {
    void enterScope();

    void exitScope();

    void releaseBinding(String bindingName);

    void recordBorrow(String bindingName, String target, boolean mutableBorrow);

    BorrowValidation validateBorrow(String target, boolean mutableBorrow);

    BorrowValidation validateMove(String target);

    BorrowValidation validateAssignment(String target);
}
