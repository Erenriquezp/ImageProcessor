package io.github.erenriquezp.rora.core.color;

/**
 * Conversión RGB 8 bits ↔ HSB (HSV) en {@code float}, sin AWT.
 *
 * <p>Implementa el algoritmo hexagonal clásico con exactamente las operaciones {@code float} que
 * produce el resultado de {@code java.awt.Color.RGBtoHSB/HSBtoRGB}; la equivalencia bit a bit se
 * verifica en tests sobre los 2^24 colores. Por eso el orden y el tipo de cada operación importan:
 * no reordenar ni pasar a {@code double}.
 */
public final class Hsb {

  private Hsb() {}

  /**
   * RGB (0–255) → {@code {h, s, b}} en {@code [0, 1]}.
   *
   * @param out array de 3 elementos a rellenar, o {@code null} para crear uno
   * @return {@code out} (o el array nuevo)
   */
  public static float[] rgbToHsb(int r, int g, int b, float[] out) {
    float[] hsb = out == null ? new float[3] : out;
    int max = Math.max(r, Math.max(g, b));
    int min = Math.min(r, Math.min(g, b));
    float value = ((float) max) / 255.0f;
    float saturation = max != 0 ? ((float) (max - min)) / ((float) max) : 0;
    float hue;
    if (saturation == 0) {
      hue = 0;
    } else {
      float range = (float) (max - min);
      float rc = ((float) (max - r)) / range;
      float gc = ((float) (max - g)) / range;
      float bc = ((float) (max - b)) / range;
      if (r == max) {
        hue = bc - gc;
      } else if (g == max) {
        hue = 2.0f + rc - bc;
      } else {
        hue = 4.0f + gc - rc;
      }
      hue = hue / 6.0f;
      if (hue < 0) {
        hue = hue + 1.0f;
      }
    }
    hsb[0] = hue;
    hsb[1] = saturation;
    hsb[2] = value;
    return hsb;
  }

  /**
   * HSB → RGB empaquetado {@code 0xFFRRGGBB}. El tono se toma módulo 1 ({@code h - floor(h)});
   * {@code s} y {@code b} se esperan en {@code [0, 1]}.
   */
  public static int hsbToRgb(float hue, float saturation, float brightness) {
    int r = 0;
    int g = 0;
    int b = 0;
    if (saturation == 0) {
      r = g = b = to8(brightness);
    } else {
      float h = (hue - (float) Math.floor(hue)) * 6.0f;
      float f = h - (float) Math.floor(h);
      float p = brightness * (1.0f - saturation);
      float q = brightness * (1.0f - saturation * f);
      float t = brightness * (1.0f - (saturation * (1.0f - f)));
      switch ((int) h) {
        case 0 -> {
          r = to8(brightness);
          g = to8(t);
          b = to8(p);
        }
        case 1 -> {
          r = to8(q);
          g = to8(brightness);
          b = to8(p);
        }
        case 2 -> {
          r = to8(p);
          g = to8(brightness);
          b = to8(t);
        }
        case 3 -> {
          r = to8(p);
          g = to8(q);
          b = to8(brightness);
        }
        case 4 -> {
          r = to8(t);
          g = to8(p);
          b = to8(brightness);
        }
        case 5 -> {
          r = to8(brightness);
          g = to8(p);
          b = to8(q);
        }
        default -> {
          // h fuera de [0, 6): no ocurre con tonos finitos; se deja en negro.
        }
      }
    }
    return 0xff000000 | (r << 16) | (g << 8) | b;
  }

  private static int to8(float v) {
    return (int) (v * 255.0f + 0.5f);
  }
}
