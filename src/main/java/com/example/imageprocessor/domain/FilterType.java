package com.example.imageprocessor.domain;

/**
 * Categorías de filtro — determinan el color del indicador en el ComboBox.
 * <ul>
 *   <li>BASIC       → azul   #4a8fd6</li>
 *   <li>TRANSPARENCY → verde  #4acc88</li>
 *   <li>RETRO       → naranja #e8913a</li>
 *   <li>CONVOLUTION → púrpura #9a6adc</li>
 *   <li>NONE        → sin dot</li>
 * </ul>
 */
public enum FilterType {
    NONE                ("Sin filtro",                  null       ),
    GRAYSCALE           ("Escala de grises",            "#4a8fd6"  ),
    NEGATIVE            ("Negativo",                    "#4a8fd6"  ),
    BRIGHTNESS          ("Brillo",                      "#4a8fd6"  ),
    HSV                 ("HSV",                         "#4a8fd6"  ),
    BW_THRESHOLD        ("Blanco y negro",              "#4a8fd6"  ),
    FROSTED             ("Esmerilado",                  "#4acc88"  ),
    CIRCULAR_FADE       ("Desvanecimiento circular",    "#4acc88"  ),
    ALPHA_GLOBAL        ("Transparencia global",        "#4acc88"  ),
    RETRO1              ("Retro 1 (cuantización RGB)",   "#e8913a"  ),
    RETRO2              ("Retro 2 (glitch canales)",    "#e8913a"  ),
    GRAYSCALE_QUANTIZED ("Grises cuantizados",          "#e8913a"  ),
    RECOLOR             ("Re-coloración",                "#e8913a"  ),
    STRETCH_4_BITS      ("Reducción y estiramiento",    "#e8913a"  ),
    CONVOLUTION         ("Convolución",                 "#9a6adc"  );

    private final String label;
    /** Hex color string for the category dot, or {@code null} for NONE. */
    private final String dotColor;

    FilterType(String label, String dotColor) {
        this.label    = label;
        this.dotColor = dotColor;
    }

    /** Returns the hex color for the category indicator dot, or {@code null} if none. */
    public String getDotColor() { return dotColor; }

    @Override
    public String toString() { return label; }
}

