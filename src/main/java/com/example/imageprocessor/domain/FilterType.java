package com.example.imageprocessor.domain;

/**
 * Categorías de filtro — determinan el color del indicador en el ComboBox.
 * <ul>
 * <li>BASIC → azul #4a8fd6</li>
 * <li>TRANSPARENCY → verde #4acc88</li>
 * <li>RETRO → naranja #e8913a</li>
 * <li>CONVOLUTION → púrpura #9a6adc</li>
 * <li>COLOR_MATRIX → rosa #e84a8f</li>
 * <li>NONE → sin dot</li>
 * </ul>
 */
public enum FilterType {
    NONE("Sin filtro", null),
    GRAYSCALE("Escala de grises", "#4a8fd6"),
    NEGATIVE("Negativo", "#4a8fd6"),
    BRIGHTNESS("Brillo", "#4a8fd6"),
    HSV("HSV", "#4a8fd6"),
    BW_THRESHOLD("Blanco y negro", "#4a8fd6"),
    FROSTED("Esmerilado", "#4acc88"),
    CIRCULAR_FADE("Desvanecimiento circular", "#4acc88"),
    ALPHA_GLOBAL("Transparencia global", "#4acc88"),
    RETRO1("Retro 1 (cuantización RGB)", "#e8913a"),
    RETRO2("Retro 2 (glitch canales)", "#e8913a"),
    GRAYSCALE_QUANTIZED("Grises cuantizados", "#e8913a"),
    RECOLOR("Re-coloración", "#e8913a"),
    BUFFER_ACUMULATION_ADD("Buffer ADD", "#ff6b6b"),
    BUFFER_ACUMULATION_ACUM("Buffer ACUMM", "#ff6b6b"),
    BUFFER_ACUMULATION_MULT("Buffer MULT", "#ff6b6b"),
    STRETCH_4_BITS("Reducción y estiramiento", "#e8913a"),
    CONVOLUTION("Convolución", "#9a6adc"),
    // ── Color Matrix ──────────────────────────────────────────────────────────
    SEPIA("Sepia", "#e84a8f"),
    COOL_TONE("Tono Frío", "#e84a8f"),
    WARM_TONE("Tono Cálido", "#e84a8f"),
    POLAROID("Polaroid", "#e84a8f"),
    KODACHROME("Kodachrome", "#e84a8f"),

    // ── Raster / Depth (#00bfa5) ─────────────────────────────────────────────
    DEPTH_MAP("Mapa de profundidad", "#00bfa5"),
    Z_BUFFER("Z-Buffer (oclusión)", "#00bfa5"),
    BITMAP_PIXELATE("Bitmap / pixelación", "#00bfa5"),
    RASTER_GRID("Rasterizar píxeles", "#00bfa5"),

    // ── Texturas / W-Buffer (#26c6da) ────────────────────────────────────────
    TEXTURE_SAMPLE("Muestreo de textura", "#26c6da"),
    DEPTH_INTERPOLATE("Interpolación en profundidad", "#26c6da"),
    W_BUFFER("W-Buffer", "#26c6da"),

    // ── Fragmentos MSAA / Alpha (#ab47bc) ───────────────────────────────────
    MULTISAMPLE("Multisample (MSAA)", "#ab47bc"),
    ALPHA_TEST("Alpha Test", "#ab47bc"),

    // ── Stencil / Blend / Logic (#5c6bc0) ───────────────────────────────────
    STENCIL_TEST("Stencil Test", "#5c6bc0"),
    FRAGMENT_BLEND("Blending de fragmentos", "#5c6bc0"),
    LOGIC_OP("Logic Op", "#5c6bc0"),

    // ── Histograma (#ffb300) ────────────────────────────────────────────────
    HISTOGRAM_EQUALIZE("Ecualización de histograma", "#ffb300"),

    // ── Operaciones por punto (#ff7043) ─────────────────────────────────────
    COLOR_ADJUST("Ajuste de color", "#ff7043"),
    POINT_INTERPOLATE("Interpolación", "#ff7043"),
    POINT_EXTRAPOLATE("Extrapolación", "#ff7043"),
    SCALE_BIAS("Escala y bias", "#ff7043"),
    POINT_THRESHOLD("Umbralización", "#ff7043"),
    TO_LUMINANCE("Conversión a luminancia", "#ff7043"),
    POINT_SATURATION("Saturación", "#ff7043"),
    HUE_ROTATE("Rotación Hue", "#ff7043"),
    COLOR_SPACE("Espacio de color", "#ff7043");

    private final String label;
    /** Hex color string for the category dot, or {@code null} for NONE. */
    private final String dotColor;

    FilterType(String label, String dotColor) {
        this.label = label;
        this.dotColor = dotColor;
    }

    /**
     * Returns the hex color for the category indicator dot, or {@code null} if
     * none.
     */
    public String getDotColor() {
        return dotColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
