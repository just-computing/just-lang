package org.justlang.compiler.borrow;

/**
 * Contract for tracking lexical borrow lifetimes and conflict checks during type checking.
 *
 * <p>Implementations must allow callers to:
 * <ul>
 *   <li>enter/exit lexical scopes,</li>
 *   <li>register and release borrows attached to local bindings,</li>
 *   <li>query whether a target currently has active borrows, and</li>
 *   <li>produce user-facing conflict messages when a new borrow is invalid.</li>
 * </ul>
 */
public interface BorrowTracker {
    void enterScope();

    void exitScope();

    boolean hasActiveBorrow(String target);

    String borrowConflict(String target, boolean mutableBorrow);

    void addBindingBorrow(String bindingName, String target, boolean mutableBorrow);

    void releaseBindingLoan(String bindingName);
}
