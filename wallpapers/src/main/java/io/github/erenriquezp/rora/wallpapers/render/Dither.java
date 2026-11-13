package io.github.erenriquezp.rora.wallpapers.render;

/**
 * Tramado determinista: ruido uniforme de 1 LSB pico a pico que depende solo de {@code (x, y,
 * canal)}. Sin estado ni semilla: el mismo píxel recibe siempre el mismo valor.
 *
 * <p>Medido en la Puerta 0: en PNG baja el error de banding de una rampa oscura de 0,115 a 0,003
 * niveles; en JPEG hasta q95 la compresión lo borra y queda peor que sin tramar, por eso solo se
 * usa con PNG.
 */
public final class Dither {

  private Dither() {}

  /** Desplazamiento en {@code [0, 1)} para {@link SrgbEncoder#quantize}. */
  public static float offset(int x, int y, int channel) {
    int h = x * 0x9E3779B1 ^ y * 0x85EBCA77 ^ channel * 0xC2B2AE3D;
    h ^= h >>> 16;
    h *= 0x7FEB352D;
    h ^= h >>> 15;
    h *= 0x846CA68B;
    h ^= h >>> 16;
    return (h >>> 8) * (1f / (1 << 24));
  }

  /** Lineal → ARGB opaco, con tramado si {@code dither} y redondeo si no. */
  public static int pixel(float r, float g, float b, int x, int y, boolean dither) {
    if (!dither) {
      return 0xFF000000
          | SrgbEncoder.quantize(r, 0.5f) << 16
          | SrgbEncoder.quantize(g, 0.5f) << 8
          | SrgbEncoder.quantize(b, 0.5f);
    }
    return 0xFF000000
        | SrgbEncoder.quantize(r, offset(x, y, 0)) << 16
        | SrgbEncoder.quantize(g, offset(x, y, 1)) << 8
        | SrgbEncoder.quantize(b, offset(x, y, 2));
  }
}
