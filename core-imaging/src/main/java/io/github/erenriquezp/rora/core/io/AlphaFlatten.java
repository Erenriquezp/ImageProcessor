package io.github.erenriquezp.rora.core.io;

/** Aplanado de transparencia con aritmética entera: {@code (a*c + (255-a)*bg + 127) / 255}. */
public final class AlphaFlatten {

  private AlphaFlatten() {}

  /** {@code argb} compuesto sobre {@code background} (0xRRGGBB), con alfa 255. */
  public static int over(int argb, int background) {
    int a = argb >>> 24;
    if (a == 255) {
      return argb;
    }
    int r = blend(a, argb >> 16 & 0xFF, background >> 16 & 0xFF);
    int g = blend(a, argb >> 8 & 0xFF, background >> 8 & 0xFF);
    int b = blend(a, argb & 0xFF, background & 0xFF);
    return 0xFF000000 | r << 16 | g << 8 | b;
  }

  /** Aplana {@code px} en el propio array. */
  public static void flatten(int[] px, int background) {
    for (int i = 0; i < px.length; i++) {
      px[i] = over(px[i], background);
    }
  }

  private static int blend(int a, int c, int bg) {
    return (a * c + (255 - a) * bg + 127) / 255;
  }
}
