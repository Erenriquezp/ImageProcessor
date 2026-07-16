package com.example.imageprocessor.domain;

/** Origen del valor de profundidad por píxel (pipeline de rasterización). */
public enum DepthSource {
    LUMINANCE("Luminancia"),
    RADIAL("Radial (centro)"),
    HORIZONTAL("Horizontal"),
    DIAGONAL("Diagonal");

    private final String label;

    DepthSource(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
