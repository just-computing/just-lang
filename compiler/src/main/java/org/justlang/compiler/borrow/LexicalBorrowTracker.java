package org.justlang.compiler.borrow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks active borrows while type checking a single function body.
 *
 * <p>Algorithm overview:
 * <ul>
 *   <li>Maintains a lexical scope stack ({@code scopeStack}) so borrows introduced in a scope are
 *   released automatically when that scope exits.</li>
 *   <li>Tracks per-target counters for shared/mutable borrows ({@code borrowCounts}).</li>
 *   <li>Tracks which binding introduced which borrow ({@code bindingBorrows}) so reassignment or
 *   shadowing can release the previous loan deterministically.</li>
 * </ul>
 *
 * <p>Complexity: all primary operations are O(1) average-time map/stack updates, except
 * {@link #exitScope()} which is O(k) for k bindings declared in that lexical scope.
 *
 * <p>This tracker is intentionally not thread-safe because each compilation/type-check run owns
 * its own instance and uses it from a single thread.
 */
public final class LexicalBorrowTracker implements BorrowTracker {
    // LIFO stack of lexical scopes; each scope stores the local bindings created there.
    private final Deque<List<String>> scopeStack = new ArrayDeque<>();
    private final Map<String, BorrowCounts> borrowCounts = new HashMap<>();
    private final Map<String, BorrowBinding> bindingBorrows = new HashMap<>();

    @Override
    public void enterScope() {
        scopeStack.push(new ArrayList<>());
    }

    @Override
    public void exitScope() {
        if (scopeStack.isEmpty()) {
            return;
        }
        List<String> bindings = scopeStack.pop();
        for (int i = bindings.size() - 1; i >= 0; i--) {
            releaseBindingLoan(bindings.get(i));
        }
    }

    @Override
    public boolean hasActiveBorrow(String target) {
        BorrowCounts counts = borrowCounts.get(target);
        return counts != null && (counts.shared() > 0 || counts.mutable() > 0);
    }

    @Override
    public String borrowConflict(String target, boolean mutableBorrow) {
        BorrowCounts counts = borrowCounts.get(target);
        if (counts == null) {
            return null;
        }
        if (mutableBorrow) {
            if (counts.shared() > 0 || counts.mutable() > 0) {
                return "Cannot take mutable borrow of '" + target + "' because it is already borrowed";
            }
            return null;
        }
        if (counts.mutable() > 0) {
            return "Cannot take shared borrow of '" + target + "' while a mutable borrow is active";
        }
        return null;
    }

    @Override
    public void addBindingBorrow(String bindingName, String target, boolean mutableBorrow) {
        releaseBindingLoan(bindingName);
        BorrowCounts counts = borrowCounts.getOrDefault(target, BorrowCounts.ZERO);
        BorrowCounts updated = mutableBorrow
            ? new BorrowCounts(counts.shared(), counts.mutable() + 1)
            : new BorrowCounts(counts.shared() + 1, counts.mutable());
        borrowCounts.put(target, updated);
        bindingBorrows.put(bindingName, new BorrowBinding(target, mutableBorrow));
        if (!scopeStack.isEmpty()) {
            scopeStack.peek().add(bindingName);
        }
    }

    @Override
    public void releaseBindingLoan(String bindingName) {
        BorrowBinding binding = bindingBorrows.remove(bindingName);
        if (binding == null) {
            return;
        }
        BorrowCounts counts = borrowCounts.get(binding.target());
        if (counts == null) {
            return;
        }
        BorrowCounts updated = binding.mutable()
            ? new BorrowCounts(counts.shared(), Math.max(0, counts.mutable() - 1))
            : new BorrowCounts(Math.max(0, counts.shared() - 1), counts.mutable());
        if (updated.shared() == 0 && updated.mutable() == 0) {
            borrowCounts.remove(binding.target());
        } else {
            borrowCounts.put(binding.target(), updated);
        }
    }

    private record BorrowBinding(String target, boolean mutable) {}

    private record BorrowCounts(int shared, int mutable) {
        private static final BorrowCounts ZERO = new BorrowCounts(0, 0);
    }
}
