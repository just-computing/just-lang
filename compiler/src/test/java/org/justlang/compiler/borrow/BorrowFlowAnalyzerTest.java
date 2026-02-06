package org.justlang.compiler.borrow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BorrowFlowAnalyzerTest {
    @Test
    void persistentBorrowConflictsAreReported() {
        BorrowFlowAnalyzer flow = BorrowFlowAnalyzer.lexical();
        List<String> errors = new ArrayList<>();

        flow.enterScope();
        assertTrue(flow.recordPersistentBorrow("r", "value", false, errors::add));

        assertFalse(flow.recordPersistentBorrow("w", "value", true, errors::add));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("mutable borrow")));
    }

    @Test
    void moveAndAssignmentAreDeniedWhileBorrowed() {
        BorrowFlowAnalyzer flow = BorrowFlowAnalyzer.lexical();
        List<String> errors = new ArrayList<>();

        flow.enterScope();
        assertTrue(flow.recordPersistentBorrow("r", "value", true, errors::add));

        assertFalse(flow.requireMovable("value", errors::add));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("Cannot move 'value'")));

        errors.clear();
        assertFalse(flow.requireAssignable("value", errors::add));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("Cannot assign to 'value'")));
    }

    @Test
    void bindingWriteReleasesPreviousLoan() {
        BorrowFlowAnalyzer flow = BorrowFlowAnalyzer.lexical();
        List<String> errors = new ArrayList<>();

        flow.enterScope();
        assertTrue(flow.recordPersistentBorrow("r", "value", false, errors::add));
        flow.onBindingWrite("r");

        assertTrue(flow.requireMovable("value", errors::add));
        assertTrue(flow.requireAssignable("value", errors::add));
    }

    @Test
    void exitingScopeReleasesLoans() {
        BorrowFlowAnalyzer flow = BorrowFlowAnalyzer.lexical();
        List<String> errors = new ArrayList<>();

        flow.enterScope();
        assertTrue(flow.recordPersistentBorrow("r", "value", false, errors::add));
        flow.exitScope();

        assertTrue(flow.requireMovable("value", errors::add));
        assertTrue(flow.requireAssignable("value", errors::add));
    }
}
