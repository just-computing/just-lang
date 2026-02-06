package org.justlang.compiler.borrow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LexicalBorrowAnalyzerTest {
    @Test
    void supportsCustomTrackerImplementations() {
        StubTracker tracker = new StubTracker();
        BorrowAnalyzer analyzer = new LexicalBorrowAnalyzer(tracker);

        analyzer.enterScope();
        analyzer.recordBorrow("loan", "value", false);
        analyzer.releaseBinding("loan");
        analyzer.exitScope();

        assertTrue(tracker.enteredScope);
        assertTrue(tracker.recordedBorrow);
        assertTrue(tracker.releasedBinding);
        assertTrue(tracker.exitedScope);
    }

    @Test
    void validatesBorrowConflictsThroughHighLevelApi() {
        BorrowAnalyzer analyzer = new LexicalBorrowAnalyzer();
        analyzer.enterScope();
        analyzer.recordBorrow("r", "value", false);

        BorrowValidation validation = analyzer.validateBorrow("value", true);

        assertFalse(validation.allowed());
        assertTrue(validation.message().contains("mutable borrow"));
    }

    @Test
    void validatesMoveAndAssignmentAgainstActiveBorrows() {
        BorrowAnalyzer analyzer = new LexicalBorrowAnalyzer();
        analyzer.enterScope();
        analyzer.recordBorrow("r", "value", false);

        BorrowValidation move = analyzer.validateMove("value");
        BorrowValidation assignment = analyzer.validateAssignment("value");

        assertFalse(move.allowed());
        assertTrue(move.message().contains("Cannot move 'value'"));
        assertFalse(assignment.allowed());
        assertTrue(assignment.message().contains("Cannot assign to 'value'"));
    }

    @Test
    void releaseBindingRemovesLoanForFutureOperations() {
        BorrowAnalyzer analyzer = new LexicalBorrowAnalyzer();
        analyzer.enterScope();
        analyzer.recordBorrow("r", "value", true);
        analyzer.releaseBinding("r");

        assertTrue(analyzer.validateMove("value").allowed());
        assertTrue(analyzer.validateAssignment("value").allowed());
        assertTrue(analyzer.validateBorrow("value", false).allowed());
    }

    @Test
    void exitingScopeReleasesAllTrackedLoans() {
        BorrowAnalyzer analyzer = new LexicalBorrowAnalyzer();
        analyzer.enterScope();
        analyzer.recordBorrow("a", "left", false);
        analyzer.recordBorrow("b", "right", true);

        analyzer.exitScope();

        assertTrue(analyzer.validateMove("left").allowed());
        assertTrue(analyzer.validateMove("right").allowed());
    }

    private static final class StubTracker implements BorrowTracker {
        private boolean enteredScope;
        private boolean exitedScope;
        private boolean recordedBorrow;
        private boolean releasedBinding;

        @Override
        public void enterScope() {
            enteredScope = true;
        }

        @Override
        public void exitScope() {
            exitedScope = true;
        }

        @Override
        public boolean hasActiveBorrow(String target) {
            return false;
        }

        @Override
        public String borrowConflict(String target, boolean mutableBorrow) {
            return null;
        }

        @Override
        public void addBindingBorrow(String bindingName, String target, boolean mutableBorrow) {
            recordedBorrow = true;
        }

        @Override
        public void releaseBindingLoan(String bindingName) {
            releasedBinding = true;
        }
    }
}
