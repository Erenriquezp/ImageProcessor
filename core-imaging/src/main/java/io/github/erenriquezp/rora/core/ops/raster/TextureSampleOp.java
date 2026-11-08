package io.github.erenriquezp.rora.core.ops.raster;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Remuestrea la imagen como una textura en mosaico: {@code u = (x / s) % w}, {@code v = (y / s) %
 * h} en {@code float}, con vecino más cercano o bilineal (también sobre el alpha). Port exacto de
 * {@code RasterPipelineFilters.textureSample}. Cualquier píxel de salida puede leer cualquier píxel
 * de entrada ({@link OpKind#GLOBAL}); con {@code scale = 1} es la identidad.
 */
public final class TextureSampleOp extends SimpleBandOp {

  public TextureSampleOp() {
    super(
        "raster.texture-sample",
        ParamSchema.of(
            new DecimalParam(
                "scale",
                "op.raster.texture-sample.scale",
                0.25,
                4,
                0.01,
                1.5,
                OptionalDouble.of(1)),
            new EnumParam(
                "filter",
                "op.raster.texture-sample.filter",
                List.of("nearest", "bilinear"),
                "nearest",
                Optional.of("nearest"))),
        new OpMeta(OpKind.GLOBAL, true, false, Cost.MODERATE, 0));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float s = Math.max(0.05f, params.getFloat("scale"));
    boolean bilinear = params.getEnum("filter").equals("bilinear");
    return (in, out, w, h, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        float v = (y / s) % h;
        if (v < 0) {
          v += h;
        }
        for (int x = 0, i = y * w; x < w; x++, i++) {
          float u = (x / s) % w;
          if (u < 0) {
            u += w;
          }
          out[i] = bilinear ? bilinear(in, u, v, w, h) : nearest(in, u, v, w, h);
        }
      }
    };
  }

  private static int nearest(int[] in, float u, float v, int w, int h) {
    int x = Math.max(0, Math.min(w - 1, (int) Math.floor(u)));
    int y = Math.max(0, Math.min(h - 1, (int) Math.floor(v)));
    return in[y * w + x];
  }

  private static int bilinear(int[] in, float u, float v, int w, int h) {
    int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(u)));
    int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(v)));
    int x1 = Math.max(0, Math.min(w - 1, x0 + 1));
    int y1 = Math.max(0, Math.min(h - 1, y0 + 1));
    float fx = u - x0;
    float fy = v - y0;
    int c00 = in[y0 * w + x0];
    int c10 = in[y0 * w + x1];
    int c01 = in[y1 * w + x0];
    int c11 = in[y1 * w + x1];
    int a = bilerp(c00 >>> 24, c10 >>> 24, c01 >>> 24, c11 >>> 24, fx, fy);
    int r =
        bilerp(
            (c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, fx, fy);
    int g =
        bilerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, fx, fy);
    int b = bilerp(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, fx, fy);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static int bilerp(int c00, int c10, int c01, int c11, float fx, float fy) {
    float top = c00 + (c10 - c00) * fx;
    float bot = c01 + (c11 - c01) * fx;
    return PixelOps.clamp(Math.round(top + (bot - top) * fy));
  }
}
