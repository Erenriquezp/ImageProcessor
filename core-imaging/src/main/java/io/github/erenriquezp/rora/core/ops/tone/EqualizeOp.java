package io.github.erenriquezp.rora.core.ops.tone;

import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Ecualización de histograma global (por canal RGB o por luminancia BT.709), mezclada con la
 * original según {@code factor} y con marcado opcional de zonas quemadas (rojo) u oscuras (azul).
 * Port exacto de {@code PointOpsFilters.histogramEqualize}.
 *
 * <p>Dos pasadas: (1) histograma por bandas, sumado en orden (entero ⇒ exacto); (2) LUT → mezcla →
 * marcas, fusionadas por píxel (en el legacy cada etapa es puntual, así que fusionarlas no cambia
 * el resultado). Con {@code factor ≤ 0} y sin marcas devuelve <b>la misma instancia</b> de entrada,
 * como el legacy.
 */
public final class EqualizeOp implements Operation {

  public static final String ID = "tone.equalize";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new EnumParam(
              "mode",
              "op.tone.equalize.mode",
              List.of("rgb", "luminance"),
              "rgb",
              Optional.of("rgb")),
          new DecimalParam(
              "factor", "op.tone.equalize.factor", 0, 1, 0.01, 1, OptionalDouble.of(0)),
          new BoolParam("markBurned", "op.tone.equalize.markBurned", false, Optional.of(false)),
          new BoolParam("markDark", "op.tone.equalize.markDark", false, Optional.of(false)));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, true, true, Cost.MODERATE, 0);

  @Override
  public String id() {
    return ID;
  }

  @Override
  public ParamSchema schema() {
    return SCHEMA;
  }

  @Override
  public OpMeta meta() {
    return META;
  }

  @Override
  public OpResult apply(Img src, Params params, OpContext ctx) {
    java.util.Objects.requireNonNull(src, "src");
    BandOperation.checkSchema(this, params);
    java.util.Objects.requireNonNull(ctx, "ctx");
    boolean luminance = params.getEnum("mode").equals("luminance");
    float factor = params.getFloat("factor");
    boolean markBurned = params.getBool("markBurned");
    boolean markDark = params.getBool("markDark");
    boolean marks = markBurned || markDark;
    if (factor <= 0f && !marks) {
      ctx.token().throwIfCancelled();
      return OpResult.of(src);
    }

    int w = src.width();
    int h = src.height();
    int n = w * h;
    int[] in = ImgAccess.pixels(src);
    int[] out = new int[n];

    // factor ≤ 0: la "mezcla" es la original; solo se marcan zonas. Se usan las mismas
    // comparaciones que el legacy (con NaN ninguna es cierta y se ecualiza y mezcla).
    boolean equalize = !(factor <= 0f);
    int[] mapR = null;
    int[] mapG = null;
    int[] mapB = null;
    int[] mapY = null;
    if (equalize) {
      if (luminance) {
        int[] histY = histogram(in, w, h, true, ctx);
        mapY = cdfLookup(histY, n);
      } else {
        int[] hist = histogram(in, w, h, false, ctx);
        mapR = cdfLookup(java.util.Arrays.copyOfRange(hist, 0, 256), n);
        mapG = cdfLookup(java.util.Arrays.copyOfRange(hist, 256, 512), n);
        mapB = cdfLookup(java.util.Arrays.copyOfRange(hist, 512, 768), n);
      }
    }
    boolean mix = equalize && !(factor >= 1f);
    final int[] fr = mapR;
    final int[] fg = mapG;
    final int[] fb = mapB;
    final int[] fy = mapY;

    ctx.executor()
        .forEachBand(
            in,
            out,
            w,
            h,
            0,
            1,
            ctx.token(),
            ctx.progress(),
            (s, d, width, height, y0, y1) -> {
              for (int i = y0 * width, end = y1 * width; i < end; i++) {
                int p = s[i];
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int px = p;
                if (equalize) {
                  int eq;
                  if (fy != null) {
                    float y0f = Math.max(1e-3f, PixelOps.luminance709(r, g, b));
                    float y1f = fy[PixelOps.clamp(Math.round(y0f))];
                    float sc = y1f / y0f;
                    int nr = PixelOps.clamp(Math.round(r * sc));
                    int ng = PixelOps.clamp(Math.round(g * sc));
                    int nb = PixelOps.clamp(Math.round(b * sc));
                    eq = (a << 24) | (nr << 16) | (ng << 8) | nb;
                  } else {
                    eq = (a << 24) | (fr[r] << 16) | (fg[g] << 8) | fb[b];
                  }
                  if (mix) {
                    int rEq = (eq >> 16) & 0xFF;
                    int gEq = (eq >> 8) & 0xFF;
                    int bEq = eq & 0xFF;
                    int mr = PixelOps.clamp(Math.round(r * (1f - factor) + rEq * factor));
                    int mg = PixelOps.clamp(Math.round(g * (1f - factor) + gEq * factor));
                    int mb = PixelOps.clamp(Math.round(b * (1f - factor) + bEq * factor));
                    px = (a << 24) | (mr << 16) | (mg << 8) | mb;
                  } else {
                    px = eq;
                  }
                }
                if (marks) {
                  int pa = (px >> 24) & 0xFF;
                  int pr = (px >> 16) & 0xFF;
                  int pg = (px >> 8) & 0xFF;
                  int pb = px & 0xFF;
                  if (markBurned && pr >= 240 && pg >= 240 && pb >= 240) {
                    px = (pa << 24) | (255 << 16);
                  } else if (markDark && pr <= 15 && pg <= 15 && pb <= 15) {
                    px = (pa << 24) | 255;
                  }
                }
                d[i] = px;
              }
            });
    return OpResult.of(Img.adopt(w, h, out));
  }

  /**
   * Histograma por bandas: 768 cubetas (R, G, B) o 256 de luminancia {@code clamp(round(Y709))}.
   */
  private static int[] histogram(int[] in, int w, int h, boolean luminance, OpContext ctx) {
    int bins = luminance ? 256 : 768;
    return ctx.executor()
        .reduceBands(
            h,
            ctx.token(),
            Progress.NONE,
            () -> new int[bins],
            (acc, y0, y1) -> {
              for (int i = y0 * w, end = y1 * w; i < end; i++) {
                int p = in[i];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (luminance) {
                  acc[PixelOps.clamp(Math.round(PixelOps.luminance709(r, g, b)))]++;
                } else {
                  acc[r]++;
                  acc[256 + g]++;
                  acc[512 + b]++;
                }
              }
            },
            (x, y) -> {
              for (int i = 0; i < x.length; i++) {
                x[i] += y[i];
              }
              return x;
            });
  }

  /** LUT de la CDF normalizada: {@code T(v) = round(255 · (CDF(v) − CDFmin) / (n − CDFmin))}. */
  static int[] cdfLookup(int[] hist, int n) {
    int[] map = new int[256];
    int cdf = 0;
    int cdfMin = 0;
    for (int v = 0; v < 256; v++) {
      cdf += hist[v];
      if (cdfMin == 0 && cdf > 0) {
        cdfMin = cdf;
      }
      if (n == cdfMin) {
        map[v] = 0;
      } else {
        map[v] = PixelOps.clamp(Math.round(255f * (cdf - cdfMin) / (n - cdfMin)));
      }
    }
    return map;
  }
}
