package com.example.imageprocessor.domain;

/** Modo de ecualización del histograma. */
public enum EqualizeMode {
    RGB("RGB (por canal)"),
    LUMINANCE("Luminancia");

    private final String label;

    EqualizeMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
