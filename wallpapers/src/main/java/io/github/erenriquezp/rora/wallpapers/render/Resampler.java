package io.github.erenriquezp.rora.wallpapers.render;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import java.util.Arrays;
import java.util.Objects;

/**
 * Remuestrea un encuadre fraccionario de la fuente a un rectángulo exacto de la salida, en luz
 * lineal: promedio por área al reducir y Lanczos-3 al ampliar, por eje. Convención de muestreo en
 * {@link Fit#sourceIndex}; los bordes replican el último píxel. La fuente debe ser opaca (el alfa
 * se aplana antes).
 *
 * <p>Determinista e independiente del número de hilos: cada fila de salida se calcula con las
 * mismas sumas en el mismo orden.
 */
public final class Resampler {

  private Resampler() {}

  /** Filtro de un eje. */
  enum Kernel {
    AREA,
    LANCZOS3
  }

  /** Pesos por posición de salida a lo largo de un eje. */
  static final class Axis {
    final int taps;
    final int[] index;
    final float[] weight;

    Axis(int srcDim, double r0, double len, int n) {
      double scale = len / n;
      Kernel k = scale > 1 ? Kernel.AREA : Kernel.LANCZOS3;
      double support = k == Kernel.LANCZOS3 ? 3.0 : scale / 2 + 0.5;
      taps = (int) Math.ceil(support * 2) + 1;
      index = new int[n * taps];
      weight = new float[n * taps];
      double[] w = new double[taps];
      for (int i = 0; i < n; i++) {
        double center = Fit.sourceIndex(i, r0, len, n);
        int first = (int) Math.floor(center - support) + 1;
        double total = 0;
        for (int t = 0; t < taps; t++) {
          int s = first + t;
          if (k == Kernel.LANCZOS3) {
            w[t] = lanczos3(s - center);
          } else {
            // Solape exacto entre el píxel fuente [s-0,5, s+0,5] y la huella del de salida.
            double half = scale / 2;
            w[t] = Math.max(0, Math.min(s + 0.5, center + half) - Math.max(s - 0.5, center - half));
          }
          total += w[t];
        }
        for (int t = 0; t < taps; t++) {
          index[i * taps + t] = Math.max(0, Math.min(srcDim - 1, first + t));
          weight[i * taps + t] = (float) (w[t] / total);
        }
      }
    }

    static double lanczos3(double x) {
      double d = Math.abs(x);
      if (d < 1e-12) {
        return 1;
      }
      if (d >= 3) {
        return 0;
      }
      double pi = Math.PI * d;
      return 3 * Math.sin(pi) * Math.sin(pi / 3) / (pi * pi);
    }
  }

  /** Remuestrea {@code frame} a una imagen nueva de exactamente {@code width × height}. */
  public static Img resample(
      Img src,
      FrameRect frame,
      int width,
      int height,
      TileExecutor executor,
      CancellationToken token,
      Progress progress) {
    int[] out = new int[width * height];
    resampleInto(
        src, frame, new IntRect(0, 0, width, height), out, width, executor, token, progress);
    return Img.adopt(width, height, out);
  }

  /**
   * Remuestrea {@code frame} sobre {@code dst} dentro de un búfer ARGB de ancho {@code stride}.
   * Solo escribe los píxeles de {@code dst}; salida opaca y redondeada (sin tramado).
   */
  public static void resampleInto(
      Img src,
      FrameRect frame,
      IntRect dst,
      int[] out,
      int stride,
      TileExecutor executor,
      CancellationToken token,
      Progress progress) {
    Objects.requireNonNull(src, "src");
    Objects.requireNonNull(frame, "frame");
    int sw = src.width();
    int sh = src.height();
    int dw = dst.w();
    int dh = dst.h();
    if (dst.right() > stride || (long) dst.bottom() * stride > out.length) {
      throw new IllegalArgumentException("el destino no cabe en el búfer");
    }
    Axis xs = new Axis(sw, frame.x(), frame.w(), dw);
    Axis ys = new Axis(sh, frame.y(), frame.h(), dh);
    executor.reduceBands(
        dh,
        token,
        progress,
        () -> null,
        (unused, y0, y1) -> {
          int slots = ys.taps + 2;
          float[][] cache = new float[slots][3 * dw];
          int[] cached = new int[slots];
          Arrays.fill(cached, -1);
          int[] row = new int[sw];
          float[] acc = new float[3 * dw];
          for (int y = y0; y < y1; y++) {
            Arrays.fill(acc, 0f);
            for (int t = 0; t < ys.taps; t++) {
              float wy = ys.weight[y * ys.taps + t];
              if (wy == 0f) {
                continue;
              }
              int sy = ys.index[y * ys.taps + t];
              int slot = sy % slots;
              if (cached[slot] != sy) {
                src.copyRow(sy, row, 0);
                horizontal(row, xs, dw, cache[slot]);
                cached[slot] = sy;
              }
              float[] v = cache[slot];
              for (int c = 0; c < acc.length; c++) {
                acc[c] += wy * v[c];
              }
            }
            int o = (dst.y() + y) * stride + dst.x();
            for (int x = 0, c = 0; x < dw; x++, c += 3) {
              out[o + x] =
                  0xFF000000
                      | SrgbEncoder.quantize(acc[c], 0.5f) << 16
                      | SrgbEncoder.quantize(acc[c + 1], 0.5f) << 8
                      | SrgbEncoder.quantize(acc[c + 2], 0.5f);
            }
          }
        },
        (a, b) -> null);
  }

  private static void horizontal(int[] row, Axis xs, int dw, float[] out) {
    for (int x = 0, c = 0; x < dw; x++, c += 3) {
      float r = 0;
      float g = 0;
      float b = 0;
      int base = x * xs.taps;
      for (int t = 0; t < xs.taps; t++) {
        float w = xs.weight[base + t];
        if (w == 0f) {
          continue;
        }
        int p = row[xs.index[base + t]];
        r += w * SrgbEncoder.decode((p >> 16) & 0xFF);
        g += w * SrgbEncoder.decode((p >> 8) & 0xFF);
        b += w * SrgbEncoder.decode(p & 0xFF);
      }
      out[c] = r;
      out[c + 1] = g;
      out[c + 2] = b;
    }
  }
}
