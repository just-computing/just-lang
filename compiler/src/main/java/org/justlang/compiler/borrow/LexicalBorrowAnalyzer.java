package org.justlang.compiler.borrow;

import java.util.Objects;

/**
 * Default borrow analyzer for v1: lexical scope + conflict counters.
 *
 * <p>Internally delegates state tracking to {@link BorrowTracker} and centralizes policy-level
 * diagnostics for move/assignment/borrow operations.
 */
public final class LexicalBorrowAnalyzer implements BorrowAnalyzer {
    private final BorrowTracker tracker;

    public LexicalBorrowAnalyzer() {
        this(new LexicalBorrowTracker());
    }

    public LexicalBorrowAnalyzer(BorrowTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @Override
    public void enterScope() {
        tracker.enterScope();
    }

    @Override
    public void exitScope() {
        tracker.exitScope();
    }

    @Override
    public void releaseBinding(String bindingName) {
        tracker.releaseBindingLoan(bindingName);
    }

    @Override
    public void recordBorrow(String bindingName, String target, boolean mutableBorrow) {
        // "binding" here is the local variable that holds the borrow (e.g. `let r = &x;` -> bindingName = "r").
        tracker.addBindingBorrow(bindingName, target, mutableBorrow);
    }

    @Override
    public BorrowValidation validateBorrow(String target, boolean mutableBorrow) {
        String conflict = tracker.borrowConflict(target, mutableBorrow);
        return conflict == null ? BorrowValidation.ok() : BorrowValidation.error(conflict);
    }

    @Override
    public BorrowValidation validateMove(String target) {
        if (!tracker.hasActiveBorrow(target)) {
            return BorrowValidation.ok();
        }
        return BorrowValidation.error("Cannot move '" + target + "' while it is borrowed");
    }

    @Override
    public BorrowValidation validateAssignment(String target) {
        if (!tracker.hasActiveBorrow(target)) {
            return BorrowValidation.ok();
        }
        return BorrowValidation.error("Cannot assign to '" + target + "' while it is borrowed");
    }
}
