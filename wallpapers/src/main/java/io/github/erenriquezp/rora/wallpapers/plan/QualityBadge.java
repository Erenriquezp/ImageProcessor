package io.github.erenriquezp.rora.wallpapers.plan;

/**
 * Calidad según la escala efectiva (cuánto se amplía la fuente). Umbrales de la Puerta 0: a ×1,5 se
 * ve bien, a ×2 blando pero aceptable y a ×3 claramente borroso.
 */
public enum QualityBadge {
  EXCELLENT,
  ACCEPTABLE,
  BLURRY;

  /** Hasta aquí, excelente. */
  public static final double EXCELLENT_MAX = 1.15;

  /** Hasta aquí, aceptable. */
  public static final double ACCEPTABLE_MAX = 2.0;

  /** A partir de aquí se sugiere mejorar la fuente con IA. */
  public static final double SUGGEST_AI = 1.5;

  public static QualityBadge of(double effectiveScale) {
    if (effectiveScale <= EXCELLENT_MAX) {
      return EXCELLENT;
    }
    return effectiveScale <= ACCEPTABLE_MAX ? ACCEPTABLE : BLURRY;
  }
}
