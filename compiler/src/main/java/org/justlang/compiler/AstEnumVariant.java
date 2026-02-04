package org.justlang.compiler;

public final class AstEnumVariant {
    private final String name;
    private final String payloadType;

    public AstEnumVariant(String name, String payloadType) {
        this.name = name;
        this.payloadType = payloadType;
    }

    public String name() {
        return name;
    }

    public String payloadType() {
        return payloadType;
    }
}
