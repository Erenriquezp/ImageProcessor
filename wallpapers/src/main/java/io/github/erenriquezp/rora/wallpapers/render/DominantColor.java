package io.github.erenriquezp.rora.wallpapers.render;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;

/**
 * Color dominante: k-means con k = 4 en luz lineal sobre una miniatura de 64 px de lado mayor,
 * semillas en los cuantiles de luminancia 1/8, 3/8, 5/8 y 7/8 y 10 iteraciones. Gana el grupo más
 * poblado (empate: el de menor índice). Determinista y de un solo hilo.
 */
public final class DominantColor {

  static final int THUMB = 64;
  static final int K = 4;
  static final int ITERATIONS = 10;

  private DominantColor() {}

  /** Color dominante en luz lineal: {@code {r, g, b}}. La fuente debe ser opaca. */
  public static float[] of(Img source, CancellationToken token) {
    int w = source.width();
    int h = source.height();
    Img t = source;
    if (Math.max(w, h) > THUMB) {
      double k = (double) THUMB / Math.max(w, h);
      t =
          Downscale.areaAverage(
              source,
              (int) Math.max(1, Math.round(w * k)),
              (int) Math.max(1, Math.round(h * k)),
              TileExecutor.sequential(),
              token,
              Progress.NONE);
    }
    int[] px = t.toArray();
    int n = px.length;
    float[][] c = new float[n][3];
    float[] lum = new float[n];
    Integer[] order = new Integer[n];
    for (int i = 0; i < n; i++) {
      int p = px[i];
      c[i][0] = SrgbEncoder.decode((p >> 16) & 0xFF);
      c[i][1] = SrgbEncoder.decode((p >> 8) & 0xFF);
      c[i][2] = SrgbEncoder.decode(p & 0xFF);
      lum[i] = 0.2126f * c[i][0] + 0.7152f * c[i][1] + 0.0722f * c[i][2];
      order[i] = i;
    }
    java.util.Arrays.sort(
        order, (a, b) -> lum[a] != lum[b] ? Float.compare(lum[a], lum[b]) : a - b);
    float[][] centers = new float[K][];
    for (int k = 0; k < K; k++) {
      centers[k] = c[order[Math.min(n - 1, (2 * k + 1) * n / (2 * K))]].clone();
    }
    int[] label = new int[n];
    int[] count = new int[K];
    for (int it = 0; it < ITERATIONS; it++) {
      token.throwIfCancelled();
      double[][] sum = new double[K][3];
      java.util.Arrays.fill(count, 0);
      for (int i = 0; i < n; i++) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < K; k++) {
          double dr = c[i][0] - centers[k][0];
          double dg = c[i][1] - centers[k][1];
          double db = c[i][2] - centers[k][2];
          double d = dr * dr + dg * dg + db * db;
          if (d < bd) {
            bd = d;
            best = k;
          }
        }
        label[i] = best;
        count[best]++;
        sum[best][0] += c[i][0];
        sum[best][1] += c[i][1];
        sum[best][2] += c[i][2];
      }
      for (int k = 0; k < K; k++) {
        if (count[k] > 0) {
          centers[k] =
              new float[] {
                (float) (sum[k][0] / count[k]),
                (float) (sum[k][1] / count[k]),
                (float) (sum[k][2] / count[k])
              };
        }
      }
    }
    int best = 0;
    for (int k = 1; k < K; k++) {
      if (count[k] > count[best]) {
        best = k;
      }
    }
    return centers[best].clone();
  }
}
