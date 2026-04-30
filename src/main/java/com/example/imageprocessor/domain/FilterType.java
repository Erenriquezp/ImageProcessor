package com.example.imageprocessor.domain;

public enum FilterType {
    NONE("Sin filtro"),
    GRAYSCALE("Escala de grises"),
    NEGATIVE("Negativo"),
    BRIGHTNESS("Brillo"),
    HSV("HSV"),
    FROSTED("Esmerilado"),
    CIRCULAR_FADE("Desvanecimiento circular"),
    RETRO1("Retro1 (cuantizacion RGB)"),
    BW_THRESHOLD("Blanco y negro"),
    RECOLOR("Recoloracion"),
    STRETCH_4_BITS("Reduccion y estiramiento"),
    CONVOLUTION("Convolucion");

    private final String label;

    FilterType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

