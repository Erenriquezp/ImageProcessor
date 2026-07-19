package com.example.imageprocessor.domain;

/** Patrón del stencil buffer para el stencil test. */
public enum StencilPattern {
    CHECKER("Tablero"),
    CIRCLE("Círculo"),
    LUMINANCE("Por luminancia"),
    DIAMOND("Diamante"),
    STRIPES("Franjas");

    private final String label;

    StencilPattern(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
