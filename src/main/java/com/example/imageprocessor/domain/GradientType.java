package com.example.imageprocessor.domain;

public enum GradientType {
    LEFT_TO_RIGHT("Izquierda -> derecha"),
    RIGHT_TO_LEFT("Derecha -> izquierda"),
    TOP_TO_BOTTOM("Arriba -> abajo"),
    BOTTOM_TO_TOP("Abajo -> arriba"),
    RADIAL("Radial");

    private final String label;

    GradientType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

