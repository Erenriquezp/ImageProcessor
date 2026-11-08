package io.github.erenriquezp.rora.core.image;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import java.util.Objects;

/**
 * Reducción por <b>promedio de área</b> con alpha ponderado, en aritmética entera.
 *
 * <p>El píxel destino {@code (j, i)} cubre en la fuente {@code [j·W/w, (j+1)·W/w) × [i·H/h,
 * (i+1)·H/h)}. Trabajando en coordenadas multiplicadas por {@code w} (horizontal) y {@code h}
 * (vertical), el solape con cada píxel fuente es un entero {@code ox·oy}: no hay redondeos en los
 * pesos. Con {@code A = Σ a·ox·oy} y {@code N = W·H}:
 *
 * <ul>
 *   <li>{@code a' = round(A / N)};
 *   <li>si {@code A > 0}: {@code c' = round(Σ c·a·ox·oy / A)} (el color se pondera por alpha, así
 *       los píxeles transparentes no oscurecen los bordes);
 *   <li>si {@code A = 0}: {@code c' = round(Σ c·ox·oy / N)} (promedio simple, para no inventar
 *       negro en zonas totalmente transparentes).
 * </ul>
 *
 * <p>Redondeo "mitad hacia arriba" con {@code long}; sin desbordamiento ({@code Σ c·a·ox·oy ≤
 * 255²·W·H < 2^63}). Bandas de filas destino fijas: mismo resultado con cualquier número de hilos.
 */
public final class Downscale {

  private Downscale() {}

  /** Reduce {@code src} a {@code dw × dh} (no mayor que la fuente). */
  public static Img areaAverage(
      Img src, int dw, int dh, TileExecutor executor, CancellationToken token, Progress progress) {
    Objects.requireNonNull(src, "src");
    int sw = src.width();
    int sh = src.height();
    if (dw < 1 || dh < 1 || dw > sw || dh > sh) {
      throw new IllegalArgumentException(
          "Destino " + dw + "x" + dh + " no válido para " + sw + "x" + sh);
    }
    if (dw == sw && dh == sh) {
      token.throwIfCancelled();
      return Img.copyOf(sw, sh, ImgAccess.pixels(src));
    }
    Axis xs = Axis.of(sw, dw);
    Axis ys = Axis.of(sh, dh);
    int[] in = ImgAccess.pixels(src);
    int[] out = new int[dw * dh];
    long n = (long) sw * sh;
    executor.reduceBands(
        dh,
        token,
        progress,
        () -> null,
        (unused, y0, y1) -> {
          // Acumuladores por columna destino: A, ΣcA (r,g,b), Σc (r,g,b).
          long[] acc = new long[7 * dw];
          long[] row = new long[7 * dw];
          for (int i = y0; i < y1; i++) {
            java.util.Arrays.fill(acc, 0L);
            for (int t = ys.start[i], end = ys.start[i + 1]; t < end; t++) {
              int y = ys.src[t];
              long oy = ys.weight[t];
              horizontal(in, y * sw, xs, dw, row);
              for (int k = 0; k < acc.length; k++) {
                acc[k] += row[k] * oy;
              }
            }
            int base = i * dw;
            for (int j = 0; j < dw; j++) {
              int o = 7 * j;
              long a = acc[o];
              int ao = (int) ((2 * a + n) / (2 * n));
              int r;
              int g;
              int b;
              if (a > 0) {
                r = (int) ((2 * acc[o + 1] + a) / (2 * a));
                g = (int) ((2 * acc[o + 2] + a) / (2 * a));
                b = (int) ((2 * acc[o + 3] + a) / (2 * a));
              } else {
                r = (int) ((2 * acc[o + 4] + n) / (2 * n));
                g = (int) ((2 * acc[o + 5] + n) / (2 * n));
                b = (int) ((2 * acc[o + 6] + n) / (2 * n));
              }
              out[base + j] = (ao << 24) | (r << 16) | (g << 8) | b;
            }
          }
        },
        (x, y) -> null);
    return Img.adopt(dw, dh, out);
  }

  /** Sumas horizontales ponderadas de una fila fuente para cada columna destino. */
  private static void horizontal(int[] in, int rowOff, Axis xs, int dw, long[] row) {
    for (int j = 0; j < dw; j++) {
      long a = 0;
      long cr = 0;
      long cg = 0;
      long cb = 0;
      long rr = 0;
      long rg = 0;
      long rb = 0;
      for (int t = xs.start[j], end = xs.start[j + 1]; t < end; t++) {
        int p = in[rowOff + xs.src[t]];
        long w = xs.weight[t];
        long pa = (p >>> 24) * w;
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        a += pa;
        cr += r * pa;
        cg += g * pa;
        cb += b * pa;
        rr += r * w;
        rg += g * w;
        rb += b * w;
      }
      int o = 7 * j;
      row[o] = a;
      row[o + 1] = cr;
      row[o + 2] = cg;
      row[o + 3] = cb;
      row[o + 4] = rr;
      row[o + 5] = rg;
      row[o + 6] = rb;
    }
  }

  /**
   * Correspondencia de un eje: para el índice destino {@code d}, los píxeles fuente {@code
   * src[start[d] .. start[d+1])} con pesos enteros {@code weight} (solapes en unidades de 1/dst).
   */
  private record Axis(int[] start, int[] src, int[] weight) {
    static Axis of(int s, int d) {
      int[] start = new int[d + 1];
      int count = 0;
      for (int k = 0; k < d; k++) {
        long lo = (long) k * s;
        long hi = (long) (k + 1) * s;
        int first = (int) (lo / d);
        int last = (int) ((hi - 1) / d);
        start[k] = count;
        count += last - first + 1;
      }
      start[d] = count;
      int[] src = new int[count];
      int[] weight = new int[count];
      int t = 0;
      for (int k = 0; k < d; k++) {
        long lo = (long) k * s;
        long hi = (long) (k + 1) * s;
        int first = (int) (lo / d);
        int last = (int) ((hi - 1) / d);
        for (int x = first; x <= last; x++) {
          long pLo = (long) x * d;
          long pHi = (long) (x + 1) * d;
          src[t] = x;
          weight[t] = (int) (Math.min(pHi, hi) - Math.max(pLo, lo));
          t++;
        }
      }
      return new Axis(start, src, weight);
    }
  }
}
