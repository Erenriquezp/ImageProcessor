package io.github.erenriquezp.rora.core.ops;

import io.github.erenriquezp.rora.core.color.SrgbLut;

/**
 * Empaqueta un color lineal fuera de gamut en ARGB de 8 bits. Mezcla el recorte por canal, que
 * responde con suavidad a excesos mínimos, con la compresión hacia la luminancia, que conserva el
 * tono en excesos grandes. La compresión sola es muy sensible cerca de {@code Y = 1}: una subida de
 * 0,01 EV movía 35 niveles un canal casi negro de un color saturado.
 */
public final class LinearPack {

  /** Exceso a partir del cual solo actúa la compresión. */
  private static final float FULL_COMPRESSION_AT = 0.25f;

  private LinearPack() {}

  /**
   * @param alphaBits alpha ya en su posición ({@code p & 0xFF000000})
   * @param luma luminancia lineal del color
   */
  public static int pack(int alphaBits, float r, float g, float b, float luma) {
    float max = Math.max(r, Math.max(g, b));
    float min = Math.min(r, Math.min(g, b));
    if (max > 1f || min < 0f) {
      float clippedR = clamp01(r);
      float clippedG = clamp01(g);
      float clippedB = clamp01(b);
      float t = Math.min(1f, Math.max(max - 1f, -min) / FULL_COMPRESSION_AT);
      float mix = t * t * (3 - 2 * t);
      float compressedR;
      float compressedG;
      float compressedB;
      if (luma >= 1f) {
        compressedR = 1f;
        compressedG = 1f;
        compressedB = 1f;
      } else if (luma <= 0f) {
        compressedR = 0f;
        compressedG = 0f;
        compressedB = 0f;
      } else {
        float k = 1f;
        if (max > 1f) {
          k = Math.min(k, (1f - luma) / (max - luma));
        }
        if (min < 0f) {
          k = Math.min(k, luma / (luma - min));
        }
        compressedR = luma + k * (r - luma);
        compressedG = luma + k * (g - luma);
        compressedB = luma + k * (b - luma);
      }
      r = clippedR + mix * (compressedR - clippedR);
      g = clippedG + mix * (compressedG - clippedG);
      b = clippedB + mix * (compressedB - clippedB);
    }
    return alphaBits | (SrgbLut.toSrgb8(r) << 16) | (SrgbLut.toSrgb8(g) << 8) | SrgbLut.toSrgb8(b);
  }

  private static float clamp01(float v) {
    return v >= 0f ? Math.min(v, 1f) : 0f;
  }
}
