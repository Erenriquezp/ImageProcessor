package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.OptionalInt;

/**
 * Redimensiona conservando la proporción hasta que el lado mayor mida {@code longSide} píxeles (0 =
 * sin cambios). Reducir usa promedio por área; ampliar, Lanczos-3 con alpha premultiplicado, con un
 * tope de 4 veces el tamaño de entrada.
 */
public final class ResizeOp extends GeometryOp {

  public static final String ID = "transform.resize";

  private static final int MAX_UPSCALE = 4;
  private static final int LOBES = 3;

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new IntParam("longSide", "op.transform.resize.longSide", 0, 16384, 0, OptionalInt.of(0)));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, false, false, Cost.EXPENSIVE, 0);

  public ResizeOp() {
    super(ID, SCHEMA, META);
  }

  @Override
  public Size outputSize(Size in, Params p) {
    int target = p.getInt("longSide");
    int side = in.longSide();
    if (target <= 0 || target == side) {
      return in;
    }
    target = Math.min(target, side * MAX_UPSCALE);
    double k = (double) target / side;
    return new Size(
        (int) Math.max(1, Math.round(in.width() * k)),
        (int) Math.max(1, Math.round(in.height() * k)));
  }

  /** En el preview nunca se amplía: el destino es, como mucho, lo que ya da el proxy. */
  @Override
  public Params forPreview(Params p, double scale, Size fullIn, Size previewIn) {
    Size full = outputSize(fullIn, p);
    int side = Math.min(full.longSide(), previewIn.longSide());
    return p.toBuilder().set("longSide", side).build();
  }

  @Override
  public OpResult apply(Img src, Params params, OpContext ctx) {
    Size in = new Size(src.width(), src.height());
    Size out = outputSize(in, params);
    if (out.width() <= in.width() && out.height() <= in.height()) {
      BandOperation.checkSchema(this, params);
      return OpResult.of(
          Downscale.areaAverage(
              src, out.width(), out.height(), ctx.executor(), ctx.token(), ctx.progress()));
    }
    return super.apply(src, params, ctx);
  }

  @Override
  RowFiller prepare(Params p, Size in, Size out) {
    Kernel xs = Kernel.of(in.width(), out.width());
    Kernel ys = Kernel.of(in.height(), out.height());
    return (src, inW, inH, dst, outW, outH, y0, y1) -> {
      int taps = ys.taps;
      float[][] cache = new float[taps * 2][4 * outW];
      int[] cachedRow = new int[taps * 2];
      java.util.Arrays.fill(cachedRow, -1);
      float[] sum = new float[4 * outW];
      for (int y = y0; y < y1; y++) {
        java.util.Arrays.fill(sum, 0f);
        for (int t = 0; t < taps; t++) {
          int row = ys.source[y * taps + t];
          float weight = ys.weight[y * taps + t];
          int slot = row % cache.length;
          if (cachedRow[slot] != row) {
            horizontal(src, inW, row, xs, outW, cache[slot]);
            cachedRow[slot] = row;
          }
          float[] values = cache[slot];
          for (int c = 0; c < sum.length; c++) {
            sum[c] += weight * values[c];
          }
        }
        for (int x = 0, c = 0, o = y * outW; x < outW; x++, c += 4, o++) {
          float a = sum[c + 3];
          if (a <= 0f) {
            dst[o] = 0;
            continue;
          }
          dst[o] =
              (channel(a) << 24)
                  | (channel(sum[c] / a) << 16)
                  | (channel(sum[c + 1] / a) << 8)
                  | channel(sum[c + 2] / a);
        }
      }
    };
  }

  private static void horizontal(int[] src, int inW, int row, Kernel xs, int outW, float[] out) {
    int offset = row * inW;
    for (int x = 0, c = 0; x < outW; x++, c += 4) {
      float r = 0f;
      float g = 0f;
      float b = 0f;
      float a = 0f;
      for (int t = 0; t < xs.taps; t++) {
        int p = src[offset + xs.source[x * xs.taps + t]];
        float weight = xs.weight[x * xs.taps + t];
        float pa = (p >>> 24) * weight;
        r += ((p >> 16) & 0xFF) * pa;
        g += ((p >> 8) & 0xFF) * pa;
        b += (p & 0xFF) * pa;
        a += pa;
      }
      out[c] = r;
      out[c + 1] = g;
      out[c + 2] = b;
      out[c + 3] = a;
    }
  }

  private static int channel(float v) {
    return (int) (Math.max(0f, Math.min(255f, v)) + 0.5f);
  }

  /** Pesos Lanczos-3 por coordenada destino; si el eje no cambia, un solo peso. */
  private static final class Kernel {
    final int taps;
    final int[] source;
    final float[] weight;

    private Kernel(int taps, int[] source, float[] weight) {
      this.taps = taps;
      this.source = source;
      this.weight = weight;
    }

    static Kernel of(int in, int out) {
      if (in >= out) {
        return nearestOrAverage(in, out);
      }
      int taps = 2 * LOBES;
      int[] source = new int[out * taps];
      float[] weight = new float[out * taps];
      double ratio = (double) in / out;
      for (int i = 0; i < out; i++) {
        double center = (i + 0.5) * ratio - 0.5;
        int first = (int) Math.floor(center) - LOBES + 1;
        double total = 0;
        double[] w = new double[taps];
        for (int t = 0; t < taps; t++) {
          w[t] = lanczos(center - (first + t));
          total += w[t];
        }
        for (int t = 0; t < taps; t++) {
          source[i * taps + t] = Math.max(0, Math.min(in - 1, first + t));
          weight[i * taps + t] = (float) (w[t] / total);
        }
      }
      return new Kernel(taps, source, weight);
    }

    /** Eje sin ampliación (solo ocurre cuando el otro eje amplía por redondeo). */
    private static Kernel nearestOrAverage(int in, int out) {
      int[] source = new int[out];
      float[] weight = new float[out];
      for (int i = 0; i < out; i++) {
        source[i] = Math.min(in - 1, (int) ((i + 0.5) * in / out));
        weight[i] = 1f;
      }
      return new Kernel(1, source, weight);
    }

    private static double lanczos(double x) {
      double d = Math.abs(x);
      if (d < 1e-12) {
        return 1;
      }
      if (d >= LOBES) {
        return 0;
      }
      double pi = StrictMath.PI * d;
      return LOBES * StrictMath.sin(pi) * StrictMath.sin(pi / LOBES) / (pi * pi);
    }
  }
}
