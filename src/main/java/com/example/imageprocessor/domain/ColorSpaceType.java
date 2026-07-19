package com.example.imageprocessor.domain;

/**
 * Conversiones entre espacios de color. Los modos {@code TO_*} empaquetan el
 * espacio destino en canales R/G/B para visualización; {@code FROM_*} interpreta
 * R/G/B como ese espacio y vuelve a RGB.
 */
public enum ColorSpaceType {
    RGB_TO_YCBCR("RGB → YCbCr"),
    YCBCR_TO_RGB("YCbCr → RGB"),
    RGB_TO_HSV("RGB → HSV"),
    HSV_TO_RGB("HSV → RGB"),
    RGB_TO_HSL("RGB → HSL"),
    HSL_TO_RGB("HSL → RGB");

    private final String label;

    ColorSpaceType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
