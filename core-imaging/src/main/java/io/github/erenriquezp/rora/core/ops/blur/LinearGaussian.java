package io.github.erenriquezp.rora.core.ops.blur;

import io.github.erenriquezp.rora.core.color.SrgbLut;

/**
 * Gaussiano separable en luz lineal para {@link GaussianBlurOp} y la máscara de enfoque. Bordes por
 * replicación. Calcula por fila: primero el paso vertical desde la fuente y después el horizontal,
 * así una banda solo necesita dos filas de memoria aunque el radio sea grande.
 */
public final class LinearGaussian {

  private final float[] weights;
  private final int radius;

  /**
   * @param radiusPx radio en píxeles, mayor que 0; {@code sigma = radiusPx / 2}
   */
  public LinearGaussian(double radiusPx) {
    double sigma = radiusPx / 2;
    this.radius = haloFor(radiusPx);
    double[] raw = new double[2 * radius + 1];
    double sum = 0;
    for (int k = -radius; k <= radius; k++) {
      raw[k + radius] = StrictMath.exp(-(k * k) / (2 * sigma * sigma));
      sum += raw[k + radius];
    }
    this.weights = new float[raw.length];
    for (int i = 0; i < raw.length; i++) {
      weights[i] = (float) (raw[i] / sum);
    }
  }

  /** Filas que lee por arriba y por abajo: {@code ceil(3·sigma)}, al menos 1. */
  public static int haloFor(double radiusPx) {
    return Math.max(1, (int) Math.ceil(3 * radiusPx / 2));
  }

  public int radius() {
    return radius;
  }

  /**
   * Luminancia lineal desenfocada de la fila {@code y}; {@code out} y {@code tmp} miden {@code w}.
   */
  public void lumaRow(int[] in, int w, int h, int y, float[] out, float[] tmp) {
    java.util.Arrays.fill(tmp, 0f);
    for (int k = -radius; k <= radius; k++) {
      float weight = weights[k + radius];
      int offset = Math.max(0, Math.min(h - 1, y + k)) * w;
      for (int x = 0; x < w; x++) {
        int p = in[offset + x];
        tmp[x] +=
            weight
                * (0.2126f * SrgbLut.toLinear((p >> 16) & 0xFF)
                    + 0.7152f * SrgbLut.toLinear((p >> 8) & 0xFF)
                    + 0.0722f * SrgbLut.toLinear(p & 0xFF));
      }
    }
    for (int x = 0; x < w; x++) {
      float sum = 0f;
      for (int k = -radius; k <= radius; k++) {
        sum += weights[k + radius] * tmp[Math.max(0, Math.min(w - 1, x + k))];
      }
      out[x] = sum;
    }
  }

  /** RGBA lineal premultiplicado de la fila {@code y}, en {@code out[4·x..]}; alpha en [0, 1]. */
  public void premultipliedRow(int[] in, int w, int h, int y, float[] out, float[] tmp) {
    java.util.Arrays.fill(tmp, 0f);
    for (int k = -radius; k <= radius; k++) {
      float weight = weights[k + radius];
      int offset = Math.max(0, Math.min(h - 1, y + k)) * w;
      for (int x = 0, c = 0; x < w; x++, c += 4) {
        int p = in[offset + x];
        float a = (p >>> 24) / 255f;
        tmp[c] += weight * SrgbLut.toLinear((p >> 16) & 0xFF) * a;
        tmp[c + 1] += weight * SrgbLut.toLinear((p >> 8) & 0xFF) * a;
        tmp[c + 2] += weight * SrgbLut.toLinear(p & 0xFF) * a;
        tmp[c + 3] += weight * a;
      }
    }
    for (int x = 0, c = 0; x < w; x++, c += 4) {
      float r = 0f;
      float g = 0f;
      float b = 0f;
      float a = 0f;
      for (int k = -radius; k <= radius; k++) {
        float weight = weights[k + radius];
        int s = 4 * Math.max(0, Math.min(w - 1, x + k));
        r += weight * tmp[s];
        g += weight * tmp[s + 1];
        b += weight * tmp[s + 2];
        a += weight * tmp[s + 3];
      }
      out[c] = r;
      out[c + 1] = g;
      out[c + 2] = b;
      out[c + 3] = a;
    }
  }
}
