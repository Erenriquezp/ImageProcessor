package io.github.erenriquezp.rora.wallpapers.render;

import io.github.erenriquezp.rora.core.color.SrgbLut;

/**
 * Lineal → sRGB con precisión suficiente para que {@code decode → encode} devuelva los 256 valores
 * exactos (tabla de 65 537 entradas con interpolación; la de {@link SrgbLut} es demasiado gruesa
 * cerca del negro para cuantizar con tramado).
 */
public final class SrgbEncoder {

  private static final int SIZE = 65536;
  private static final float[] ENCODE = new float[SIZE + 1];

  static {
    for (int i = 0; i <= SIZE; i++) {
      ENCODE[i] = (float) (SrgbLut.encode(i / (double) SIZE) * 255.0);
    }
  }

  private SrgbEncoder() {}

  /** Intensidad lineal → valor sRGB continuo en {@code [0, 255]} (se recorta; NaN → 0). */
  public static float encode255(float linear) {
    if (!(linear > 0f)) {
      return 0f;
    }
    if (linear >= 1f) {
      return 255f;
    }
    float v = linear * SIZE;
    int i = (int) v;
    float f = v - i;
    return ENCODE[i] + (ENCODE[i + 1] - ENCODE[i]) * f;
  }

  /**
   * Cuantiza a 8 bits: {@code floor(encode255(linear) + offset)}. {@code offset = 0,5} redondea; un
   * valor en {@code [0, 1)} que depende de la posición es el tramado.
   */
  public static int quantize(float linear, float offset) {
    int v = (int) Math.floor(encode255(linear) + offset);
    return v < 0 ? 0 : Math.min(255, v);
  }

  /** sRGB de 8 bits → lineal. */
  public static float decode(int srgb8) {
    return SrgbLut.toLinear(srgb8);
  }
}
