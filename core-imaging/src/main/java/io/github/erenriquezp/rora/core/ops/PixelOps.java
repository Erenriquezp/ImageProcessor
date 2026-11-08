package io.github.erenriquezp.rora.core.ops;

/**
 * Aritmética compartida por las operaciones portadas. Cada método reproduce <b>literalmente</b> la
 * expresión del código heredado (mismos tipos y mismo orden); no "mejorar".
 */
public final class PixelOps {

  private PixelOps() {}

  /** {@code max(0, min(255, v))}. */
  public static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  /** {@code max(0f, min(1f, v))}. */
  public static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  /** Luminancia BT.709 en {@code float}: {@code 0.2126 R + 0.7152 G + 0.0722 B}. */
  public static float luminance709(int r, int g, int b) {
    return 0.2126f * r + 0.7152f * g + 0.0722f * b;
  }

  /**
   * Cuantiza {@code value} a {@code n} niveles repartidos en {@code [0, 255]} (en {@code double}).
   */
  public static int quantize(int value, int n) {
    if (n <= 1) {
      return 0;
    }
    if (n >= 256) {
      return value;
    }
    double step = 255.0 / (n - 1);
    int level = (int) Math.round(value / step);
    return (int) Math.round(level * step);
  }

  /** {@code clamp(round(start + t · (end − start)))} en {@code double}. */
  public static int lerp(int start, int end, double t) {
    return clamp((int) Math.round(start + t * (end - start)));
  }
}
