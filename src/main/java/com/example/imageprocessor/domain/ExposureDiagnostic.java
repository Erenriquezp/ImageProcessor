package com.example.imageprocessor.domain;

/**
 * Encapsula la información del diagnóstico de exposición de una imagen.
 */
public record ExposureDiagnostic(double pctBurned, double pctDark, String text) {
}
