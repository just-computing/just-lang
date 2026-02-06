package org.justlang.compiler.borrow;

/**
 * Result object returned by high-level borrow checks.
 *
 * <p>This keeps policy decisions (whether an operation is legal and what message to report) inside
 * the borrow package so callers can focus on control flow and diagnostics plumbing.
 */
public record BorrowValidation(boolean allowed, String message) {
    private static final BorrowValidation ALLOWED = new BorrowValidation(true, null);

    public static BorrowValidation ok() {
        return ALLOWED;
    }

    public static BorrowValidation error(String message) {
        return new BorrowValidation(false, message);
    }
}
