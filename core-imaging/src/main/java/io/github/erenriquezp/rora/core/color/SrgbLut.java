package io.github.erenriquezp.rora.core.color;

/**
 * Conversión sRGB 8 bits ↔ lineal con tablas (IEC 61966-2-1). Para fases futuras (blur, resize y
 * blend en lineal); <b>ninguna operación de la fase 1 la usa</b>, porque cambiaría sus resultados.
 */
public final class SrgbLut {

  /** Entradas de la tabla lineal → sRGB (resolución {@code 1/ENCODE_SIZE}). */
  public static final int ENCODE_SIZE = 4096;

  private static final float[] TO_LINEAR = new float[256];
  private static final byte[] TO_SRGB = new byte[ENCODE_SIZE + 1];

  static {
    for (int i = 0; i < 256; i++) {
      TO_LINEAR[i] = (float) decode(i / 255.0);
    }
    for (int i = 0; i <= ENCODE_SIZE; i++) {
      TO_SRGB[i] = (byte) Math.round(encode(i / (double) ENCODE_SIZE) * 255.0);
    }
  }

  private SrgbLut() {}

  /** Valor sRGB de 8 bits (0–255) → intensidad lineal en {@code [0, 1]}. */
  public static float toLinear(int srgb8) {
    return TO_LINEAR[srgb8 & 0xFF];
  }

  /** Intensidad lineal (se recorta a {@code [0, 1]}; NaN → 0) → sRGB de 8 bits. */
  public static int toSrgb8(float linear) {
    float v = linear >= 0f ? Math.min(linear, 1f) : 0f;
    return TO_SRGB[Math.round(v * ENCODE_SIZE)] & 0xFF;
  }

  /** sRGB normalizado → lineal (fórmula exacta en {@code double}). */
  public static double decode(double srgb) {
    return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
  }

  /** Lineal → sRGB normalizado (fórmula exacta en {@code double}). */
  public static double encode(double linear) {
    return linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
  }
}
