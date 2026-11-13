package io.github.erenriquezp.rora.wallpapers.render;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;

/** Fondos de "Contener + relleno". Todo en luz lineal; el tramado solo si se pide. */
public final class Fills {

  /** La versión desenfocada se calcula a 1/16 del lado de la salida. */
  static final int BLUR_REDUCTION = 16;

  /** Sigma del desenfoque, como fracción del lado menor de la versión reducida. */
  static final double BLUR_SIGMA = 0.06;

  /** El degradado baja hasta este factor de la luminancia del color dominante. */
  static final float GRADIENT_END = 0.4f;

  private Fills() {}

  /**
   * Pinta el fondo de toda la salida. El espejo no se pinta aquí: necesita el contenido (ver {@link
   * #mirror}).
   *
   * @param dominant color dominante en lineal (solo para el degradado)
   */
  public static void background(
      Img source,
      FillSpec fill,
      float[] dominant,
      int width,
      int height,
      int[] out,
      boolean dither,
      TileExecutor executor,
      CancellationToken token) {
    float keep = (float) (1 - fill.strength());
    switch (fill.kind()) {
      case SOLID -> java.util.Arrays.fill(out, 0, width * height, fill.solidArgb());
      case MIRROR -> {
        // se pinta después, con el contenido
      }
      case GRADIENT -> {
        float r = dominant[0] * keep;
        float g = dominant[1] * keep;
        float b = dominant[2] * keep;
        executor.reduceBands(
            height,
            token,
            Progress.NONE,
            () -> null,
            (u, y0, y1) -> {
              for (int y = y0; y < y1; y++) {
                float t = height == 1 ? 0 : (float) y / (height - 1);
                float k = 1 - (1 - GRADIENT_END) * t;
                for (int x = 0; x < width; x++) {
                  out[y * width + x] = Dither.pixel(r * k, g * k, b * k, x, y, dither);
                }
              }
            },
            (a, c) -> null);
      }
      case BLUR -> blur(source, keep, width, height, out, dither, executor, token);
    }
  }

  private static void blur(
      Img source,
      float keep,
      int width,
      int height,
      int[] out,
      boolean dither,
      TileExecutor executor,
      CancellationToken token) {
    int bw = Math.max(4, (width + BLUR_REDUCTION - 1) / BLUR_REDUCTION);
    int bh = Math.max(4, (height + BLUR_REDUCTION - 1) / BLUR_REDUCTION);
    Img small =
        Resampler.resample(
            source,
            Fit.cover(source.width(), source.height(), width, height),
            bw,
            bh,
            TileExecutor.sequential(),
            token,
            Progress.NONE);
    float[] lin = new float[3 * bw * bh];
    int[] px = small.toArray();
    for (int i = 0; i < px.length; i++) {
      lin[3 * i] = SrgbEncoder.decode((px[i] >> 16) & 0xFF) * keep;
      lin[3 * i + 1] = SrgbEncoder.decode((px[i] >> 8) & 0xFF) * keep;
      lin[3 * i + 2] = SrgbEncoder.decode(px[i] & 0xFF) * keep;
    }
    float[] blurred = gaussian(lin, bw, bh, BLUR_SIGMA * Math.min(bw, bh));
    token.throwIfCancelled();
    // Ampliación con B-spline cúbico (suave, sin sobreimpulso), separable: primero las filas.
    float[] rows = new float[3 * bh * width];
    int[] ix = new int[4 * width];
    float[] wx = new float[4 * width];
    for (int x = 0; x < width; x++) {
      double sx = Fit.sourceIndex(x, 0, bw, width);
      int i0 = (int) Math.floor(sx);
      bspline(sx - i0, wx, 4 * x);
      for (int t = 0; t < 4; t++) {
        ix[4 * x + t] = Math.max(0, Math.min(bw - 1, i0 - 1 + t));
      }
    }
    for (int y = 0; y < bh; y++) {
      for (int x = 0; x < width; x++) {
        float r = 0;
        float g = 0;
        float b = 0;
        for (int t = 0; t < 4; t++) {
          int o = 3 * (y * bw + ix[4 * x + t]);
          float w = wx[4 * x + t];
          r += w * blurred[o];
          g += w * blurred[o + 1];
          b += w * blurred[o + 2];
        }
        int o = 3 * (y * width + x);
        rows[o] = r;
        rows[o + 1] = g;
        rows[o + 2] = b;
      }
    }
    executor.reduceBands(
        height,
        token,
        Progress.NONE,
        () -> null,
        (u, y0, y1) -> {
          float[] wy = new float[4];
          for (int y = y0; y < y1; y++) {
            double sy = Fit.sourceIndex(y, 0, bh, height);
            int j0 = (int) Math.floor(sy);
            bspline(sy - j0, wy, 0);
            int[] rowsIdx = new int[4];
            for (int t = 0; t < 4; t++) {
              rowsIdx[t] = 3 * width * Math.max(0, Math.min(bh - 1, j0 - 1 + t));
            }
            for (int x = 0; x < width; x++) {
              float r = 0;
              float g = 0;
              float b = 0;
              for (int t = 0; t < 4; t++) {
                int o = rowsIdx[t] + 3 * x;
                r += wy[t] * rows[o];
                g += wy[t] * rows[o + 1];
                b += wy[t] * rows[o + 2];
              }
              out[y * width + x] = Dither.pixel(r, g, b, x, y, dither);
            }
          }
        },
        (a, c) -> null);
  }

  /** Pesos del B-spline cúbico para la fracción {@code t} en {@code [0, 1)}. */
  private static void bspline(double t, float[] w, int off) {
    double t2 = t * t;
    double t3 = t2 * t;
    w[off] = (float) ((1 - 3 * t + 3 * t2 - t3) / 6);
    w[off + 1] = (float) ((4 - 6 * t2 + 3 * t3) / 6);
    w[off + 2] = (float) ((1 + 3 * t + 3 * t2 - 3 * t3) / 6);
    w[off + 3] = (float) (t3 / 6);
  }

  /** Gaussiana separable sobre RGB lineal con bordes replicados. */
  static float[] gaussian(float[] rgb, int w, int h, double sigma) {
    int r = Math.max(1, (int) Math.ceil(sigma * 3));
    float[] k = new float[2 * r + 1];
    double sum = 0;
    for (int i = -r; i <= r; i++) {
      double v = Math.exp(-i * i / (2 * sigma * sigma));
      k[i + r] = (float) v;
      sum += v;
    }
    for (int i = 0; i < k.length; i++) {
      k[i] = (float) (k[i] / sum);
    }
    float[] tmp = new float[rgb.length];
    float[] out = new float[rgb.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        for (int c = 0; c < 3; c++) {
          float a = 0;
          for (int i = -r; i <= r; i++) {
            a += k[i + r] * rgb[3 * (y * w + Math.max(0, Math.min(w - 1, x + i))) + c];
          }
          tmp[3 * (y * w + x) + c] = a;
        }
      }
    }
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        for (int c = 0; c < 3; c++) {
          float a = 0;
          for (int i = -r; i <= r; i++) {
            a += k[i + r] * tmp[3 * (Math.max(0, Math.min(h - 1, y + i)) * w + x) + c];
          }
          out[3 * (y * w + x) + c] = a;
        }
      }
    }
    return out;
  }

  /**
   * Espejo: cada píxel fuera de {@code content} toma el del reflejo dentro (el borde se repite, así
   * que la costura es continua), oscurecido según {@code strength}.
   */
  public static void mirror(
      int[] out, int width, int height, IntRect content, double strength, boolean dither) {
    float keep = (float) (1 - strength);
    boolean plain = strength == 0 && !dither;
    for (int y = 0; y < height; y++) {
      int ry = reflect(y, content.y(), content.bottom());
      for (int x = 0; x < width; x++) {
        if (content.contains(x, y)) {
          continue;
        }
        int p = out[ry * width + reflect(x, content.x(), content.right())];
        if (plain) {
          out[y * width + x] = p;
        } else {
          out[y * width + x] =
              Dither.pixel(
                  SrgbEncoder.decode((p >> 16) & 0xFF) * keep,
                  SrgbEncoder.decode((p >> 8) & 0xFF) * keep,
                  SrgbEncoder.decode(p & 0xFF) * keep,
                  x,
                  y,
                  dither);
        }
      }
    }
  }

  /** Reflejo simétrico (repite el borde) de {@code v} en {@code [lo, hi)}. */
  static int reflect(int v, int lo, int hi) {
    int len = hi - lo;
    int m = Math.floorMod(v - lo, 2 * len);
    return m < len ? lo + m : hi - 1 - (m - len);
  }
}
