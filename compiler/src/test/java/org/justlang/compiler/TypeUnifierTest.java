package org.justlang.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TypeUnifierTest {
    @Test
    void joinHandlesInferAnyAndMismatch() {
        assertEquals(TypeId.INT, TypeUnifier.join(TypeId.INT, TypeId.INT));
        assertEquals(TypeId.INT, TypeUnifier.join(TypeId.INFER, TypeId.INT));
        assertEquals(TypeId.INT, TypeUnifier.join(TypeId.INT, TypeId.INFER));
        assertEquals(TypeId.ANY, TypeUnifier.join(TypeId.ANY, TypeId.INT));
        assertEquals(TypeId.ANY, TypeUnifier.join(TypeId.INT, TypeId.ANY));

        TypeId left = TypeId.option(TypeId.INT);
        TypeId right = TypeId.result(TypeId.INT, TypeId.STRING);
        assertEquals(TypeId.ANY, TypeUnifier.join(left, right));
    }

    @Test
    void joinJoinsReferenceOptionAndResultInnerTypes() {
        TypeId sharedInt = TypeId.reference(TypeId.INT, false);
        TypeId mutInt = TypeId.reference(TypeId.INT, true);
        assertEquals(sharedInt, TypeUnifier.join(mutInt, sharedInt));

        TypeId sharedOptInfer = TypeId.reference(TypeId.option(TypeId.INFER), false);
        TypeId sharedOptInt = TypeId.reference(TypeId.option(TypeId.INT), false);
        assertEquals(sharedOptInt, TypeUnifier.join(sharedOptInfer, sharedOptInt));

        TypeId optInfer = TypeId.option(TypeId.INFER);
        TypeId optInt = TypeId.option(TypeId.INT);
        assertEquals(optInt, TypeUnifier.join(optInfer, optInt));

        TypeId resInfer = TypeId.result(TypeId.INFER, TypeId.INFER);
        TypeId resConcrete = TypeId.result(TypeId.INT, TypeId.STRING);
        assertEquals(resConcrete, TypeUnifier.join(resInfer, resConcrete));
    }

    @Test
    void tryJoinRejectsIncompatibleTypesInsteadOfWidening() {
        assertEquals(TypeId.INT, TypeUnifier.tryJoin(TypeId.INFER, TypeId.INT));
        assertEquals(TypeId.ANY, TypeUnifier.tryJoin(TypeId.ANY, TypeId.INT));

        TypeId optInt = TypeId.option(TypeId.INT);
        TypeId optString = TypeId.option(TypeId.STRING);
        assertEquals(null, TypeUnifier.tryJoin(optInt, optString));

        TypeId mutInt = TypeId.reference(TypeId.INT, true);
        TypeId sharedInt = TypeId.reference(TypeId.INT, false);
        assertEquals(TypeId.reference(TypeId.INT, false), TypeUnifier.tryJoin(mutInt, sharedInt));
    }

    @Test
    void refineIgnoresUnknownVoidAndPreservesCurrentWhenNeeded() {
        assertEquals(TypeId.INT, TypeUnifier.refine(TypeId.INT, null));
        assertEquals(TypeId.INT, TypeUnifier.refine(TypeId.INT, TypeId.UNKNOWN));
        assertEquals(TypeId.INT, TypeUnifier.refine(TypeId.INT, TypeId.VOID));

        assertEquals(TypeId.UNKNOWN, TypeUnifier.refine(TypeId.UNKNOWN, TypeId.INT));
        assertEquals(TypeId.VOID, TypeUnifier.refine(TypeId.VOID, TypeId.INT));

        assertEquals(TypeId.ANY, TypeUnifier.refine(TypeId.ANY, TypeId.INT));
    }

    @Test
    void refinePropagatesConstraintsIntoInferAndCompositeTypes() {
        assertEquals(TypeId.INT, TypeUnifier.refine(TypeId.INFER, TypeId.INT));
        assertEquals(TypeId.INT, TypeUnifier.refine(TypeId.INT, TypeId.INFER));

        TypeId mutInferRef = TypeId.reference(TypeId.INFER, true);
        TypeId sharedIntRef = TypeId.reference(TypeId.INT, false);
        assertEquals(TypeId.reference(TypeId.INT, true), TypeUnifier.refine(mutInferRef, sharedIntRef));

        TypeId sharedInferRef = TypeId.reference(TypeId.INFER, false);
        TypeId mutIntRef = TypeId.reference(TypeId.INT, true);
        assertEquals(TypeId.reference(TypeId.INT, false), TypeUnifier.refine(sharedInferRef, mutIntRef));

        TypeId optInfer = TypeId.option(TypeId.INFER);
        TypeId optInt = TypeId.option(TypeId.INT);
        assertEquals(optInt, TypeUnifier.refine(optInfer, optInt));

        TypeId res = TypeId.result(TypeId.INFER, TypeId.STRING);
        TypeId resConstraint = TypeId.result(TypeId.INT, TypeId.INFER);
        assertEquals(TypeId.result(TypeId.INT, TypeId.STRING), TypeUnifier.refine(res, resConstraint));
    }

    @Test
    void refineKeepsCurrentOnMismatch() {
        TypeId current = TypeId.option(TypeId.INT);
        TypeId constraint = TypeId.result(TypeId.INT, TypeId.STRING);
        assertEquals(current, TypeUnifier.refine(current, constraint));
    }
}
