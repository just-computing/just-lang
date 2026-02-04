package org.justlang.compiler;

public final class AstMatchPattern {
    public enum Kind {
        WILDCARD,
        INT,
        BOOL,
        STRING,
        RANGE
    }

    private final Kind kind;
    private final String value;
    private final String rangeStart;
    private final String rangeEnd;
    private final boolean inclusive;

    private AstMatchPattern(Kind kind, String value, String rangeStart, String rangeEnd, boolean inclusive) {
        this.kind = kind;
        this.value = value;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.inclusive = inclusive;
    }

    public static AstMatchPattern wildcard() {
        return new AstMatchPattern(Kind.WILDCARD, null, null, null, false);
    }

    public static AstMatchPattern intLiteral(String value) {
        return new AstMatchPattern(Kind.INT, value, null, null, false);
    }

    public static AstMatchPattern boolLiteral(String value) {
        return new AstMatchPattern(Kind.BOOL, value, null, null, false);
    }

    public static AstMatchPattern stringLiteral(String value) {
        return new AstMatchPattern(Kind.STRING, value, null, null, false);
    }

    public static AstMatchPattern range(String start, String end, boolean inclusive) {
        return new AstMatchPattern(Kind.RANGE, null, start, end, inclusive);
    }

    public Kind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public String rangeStart() {
        return rangeStart;
    }

    public String rangeEnd() {
        return rangeEnd;
    }

    public boolean inclusive() {
        return inclusive;
    }
}
