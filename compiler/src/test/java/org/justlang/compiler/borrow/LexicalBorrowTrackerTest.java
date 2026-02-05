package org.justlang.compiler.borrow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LexicalBorrowTrackerTest {
    @Test
    void exitingScopeReleasesTrackedBindings() {
        BorrowTracker tracker = new LexicalBorrowTracker();
        tracker.enterScope();
        tracker.addBindingBorrow("loan", "value", false);

        assertTrue(tracker.hasActiveBorrow("value"));

        tracker.exitScope();

        assertFalse(tracker.hasActiveBorrow("value"));
        assertNull(tracker.borrowConflict("value", true));
    }

    @Test
    void sharedBorrowConflictsWithMutableBorrow() {
        BorrowTracker tracker = new LexicalBorrowTracker();
        tracker.enterScope();
        tracker.addBindingBorrow("loan", "value", false);

        String conflict = tracker.borrowConflict("value", true);

        assertNotNull(conflict);
        assertTrue(conflict.contains("mutable borrow"));
    }

    @Test
    void mutableBorrowConflictsWithSharedBorrow() {
        BorrowTracker tracker = new LexicalBorrowTracker();
        tracker.enterScope();
        tracker.addBindingBorrow("loan", "value", true);

        String conflict = tracker.borrowConflict("value", false);

        assertNotNull(conflict);
        assertTrue(conflict.contains("shared borrow"));
    }

    @Test
    void rebindingLoanReleasesPreviousTarget() {
        BorrowTracker tracker = new LexicalBorrowTracker();
        tracker.enterScope();
        tracker.addBindingBorrow("loan", "first", false);
        tracker.addBindingBorrow("loan", "second", false);

        assertFalse(tracker.hasActiveBorrow("first"));
        assertTrue(tracker.hasActiveBorrow("second"));
    }
}
