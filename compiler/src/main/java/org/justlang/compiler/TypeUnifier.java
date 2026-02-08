package org.justlang.compiler;

/**
 * Small helper for v1 type inference/unification.
 *
 * <p>This is not a full HM-style inference engine; it only supports:
 * <ul>
 *   <li>joining types that contain {@link TypeId#INFER} placeholders (primarily for {@code Option}/{@code Result}),</li>
 *   <li>propagating constraints into placeholder types.</li>
 * </ul>
 */
final class TypeUnifier {
    private TypeUnifier() {}

    static TypeId join(TypeId left, TypeId right) {
        if (left.equals(right)) {
            return left;
        }
        if (left == TypeId.INFER) {
            return right;
        }
        if (right == TypeId.INFER) {
            return left;
        }
        if (left == TypeId.ANY || right == TypeId.ANY) {
            return TypeId.ANY;
        }
        if (left.isReference() && right.isReference()) {
            TypeId inner = join(left.referenceInner(), right.referenceInner());
            boolean mutable = left.referenceMutable() && right.referenceMutable();
            return TypeId.reference(inner, mutable);
        }
        if (left.isOption() && right.isOption()) {
            return TypeId.option(join(left.optionInner(), right.optionInner()));
        }
        if (left.isResult() && right.isResult()) {
            return TypeId.result(
                join(left.resultOk(), right.resultOk()),
                join(left.resultErr(), right.resultErr())
            );
        }
        return TypeId.ANY;
    }

    /**
     * Strict join used when a single program point must have a single concrete type (e.g. after
     * joining control-flow branches). Returns {@code null} when there is no valid join.
     *
     * <p>Unlike {@link #join(TypeId, TypeId)}, this does not widen mismatches to {@link TypeId#ANY}.
     */
    static TypeId tryJoin(TypeId left, TypeId right) {
        if (left.equals(right)) {
            return left;
        }
        if (left == TypeId.INFER) {
            return right;
        }
        if (right == TypeId.INFER) {
            return left;
        }
        if (left == TypeId.ANY || right == TypeId.ANY) {
            return TypeId.ANY;
        }
        if (left.isReference() && right.isReference()) {
            TypeId inner = tryJoin(left.referenceInner(), right.referenceInner());
            if (inner == null) {
                return null;
            }
            boolean mutable = left.referenceMutable() && right.referenceMutable();
            return TypeId.reference(inner, mutable);
        }
        if (left.isOption() && right.isOption()) {
            TypeId inner = tryJoin(left.optionInner(), right.optionInner());
            if (inner == null) {
                return null;
            }
            return TypeId.option(inner);
        }
        if (left.isResult() && right.isResult()) {
            TypeId ok = tryJoin(left.resultOk(), right.resultOk());
            TypeId err = tryJoin(left.resultErr(), right.resultErr());
            if (ok == null || err == null) {
                return null;
            }
            return TypeId.result(ok, err);
        }
        return null;
    }

    static TypeId refine(TypeId current, TypeId constraint) {
        if (constraint == null || constraint == TypeId.UNKNOWN || constraint == TypeId.VOID) {
            return current;
        }
        if (current == TypeId.UNKNOWN || current == TypeId.VOID) {
            return current;
        }
        if (current == TypeId.INFER) {
            return constraint;
        }
        if (constraint == TypeId.INFER) {
            return current;
        }
        if (current == TypeId.ANY) {
            return current;
        }
        if (current.isReference() && constraint.isReference()) {
            TypeId inner = refine(current.referenceInner(), constraint.referenceInner());
            return TypeId.reference(inner, current.referenceMutable());
        }
        if (current.isOption() && constraint.isOption()) {
            return TypeId.option(refine(current.optionInner(), constraint.optionInner()));
        }
        if (current.isResult() && constraint.isResult()) {
            return TypeId.result(
                refine(current.resultOk(), constraint.resultOk()),
                refine(current.resultErr(), constraint.resultErr())
            );
        }
        return current;
    }
}
