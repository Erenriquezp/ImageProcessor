package com.example.imageprocessor.domain;

/** Modo de filtrado al muestrear la imagen como textura. */
public enum TextureFilterMode {
    NEAREST("Nearest (punto)"),
    BILINEAR("Bilinear");

    private final String label;

    TextureFilterMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
