package com.example.imageprocessor.domain;

/** Modos de blending de fragmentos (ecuación de mezcla). */
public enum FragmentBlendMode {
    SRC_OVER("Src Over (α)"),
    MULTIPLY("Multiply"),
    ADD("Add"),
    SCREEN("Screen"),
    SUBTRACT("Subtract");

    private final String label;

    FragmentBlendMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
