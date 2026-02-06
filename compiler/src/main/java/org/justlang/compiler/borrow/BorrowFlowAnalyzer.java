package org.justlang.compiler.borrow;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Orchestrates borrow rules over a function's lexical control flow.
 *
 * <p>This adapter is intentionally "intent"-level: callers express what they are doing
 * (borrow/move/assign/bind) and it delegates state tracking + policy decisions to a
 * {@link BorrowAnalyzer} implementation.
 */
public final class BorrowFlowAnalyzer {
    private final BorrowAnalyzer analyzer;

    public BorrowFlowAnalyzer(BorrowAnalyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
    }

    public static BorrowFlowAnalyzer lexical() {
        return new BorrowFlowAnalyzer(new LexicalBorrowAnalyzer());
    }

    public void enterScope() {
        analyzer.enterScope();
    }

    public void exitScope() {
        analyzer.exitScope();
    }

    /**
     * Signals that a local binding name is being (re)introduced, shadowed, or overwritten.
     *
     * <p>In v1 this ends any tracked loan held by the binding variable (e.g., rebinding `r` ends
     * the borrow created by `let r = &x;`). More precise shadowing semantics can be implemented
     * here without leaking details to the type checker.
     */
    public void onBindingWrite(String bindingName) {
        analyzer.releaseBinding(bindingName);
    }

    public boolean requireBorrowable(String target, boolean mutableBorrow, Consumer<String> errorSink) {
        return require(target, analyzer.validateBorrow(target, mutableBorrow), errorSink);
    }

    public boolean requireMovable(String target, Consumer<String> errorSink) {
        return require(target, analyzer.validateMove(target), errorSink);
    }

    public boolean requireAssignable(String target, Consumer<String> errorSink) {
        return require(target, analyzer.validateAssignment(target), errorSink);
    }

    public boolean recordPersistentBorrow(
        String bindingName,
        String target,
        boolean mutableBorrow,
        Consumer<String> errorSink
    ) {
        BorrowValidation validation = analyzer.validateBorrow(target, mutableBorrow);
        if (!require(target, validation, errorSink)) {
            return false;
        }
        analyzer.recordBorrow(bindingName, target, mutableBorrow);
        return true;
    }

    private boolean require(String target, BorrowValidation validation, Consumer<String> errorSink) {
        if (validation.allowed()) {
            return true;
        }
        String message = validation.message() != null ? validation.message() : "Borrow check failed for '" + target + "'";
        errorSink.accept(message);
        return false;
    }
}
