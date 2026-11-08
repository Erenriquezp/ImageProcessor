package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.OptionalDouble;

/**
 * Enderezado: gira {@code degrees} y recorta al mayor rectángulo centrado con la proporción
 * original que cabe dentro, para no dejar esquinas vacías. Interpolación bicúbica Catmull-Rom con
 * alpha premultiplicado. Con 0 grados, copia exacta.
 */
public final class StraightenOp extends GeometryOp {

  public static final String ID = "transform.straighten";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new DecimalParam(
              "degrees",
              "op.transform.straighten.degrees",
              -45,
              45,
              0.01,
              0,
              OptionalDouble.of(0)));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, false, false, Cost.MODERATE, 0);

  public StraightenOp() {
    super(ID, SCHEMA, META);
  }

  private static double radians(Params p) {
    return StrictMath.toRadians(Math.max(-45, Math.min(45, p.getDouble("degrees"))));
  }

  @Override
  public Size outputSize(Size in, Params p) {
    double angle = radians(p);
    if (angle == 0) {
      return in;
    }
    double c = Math.abs(StrictMath.cos(angle));
    double s = Math.abs(StrictMath.sin(angle));
    double w = in.width();
    double h = in.height();
    double k = Math.min(w / (w * c + h * s), h / (w * s + h * c));
    return new Size(
        (int) Math.max(1, Math.min(in.width(), Math.floor(w * k))),
        (int) Math.max(1, Math.min(in.height(), Math.floor(h * k))));
  }

  @Override
  RowFiller prepare(Params p, Size in, Size out) {
    double angle = radians(p);
    if (angle == 0) {
      return (src, inW, inH, dst, outW, outH, y0, y1) ->
          System.arraycopy(src, y0 * inW, dst, y0 * outW, (y1 - y0) * outW);
    }
    double cos = StrictMath.cos(angle);
    double sin = StrictMath.sin(angle);
    return (src, inW, inH, dst, outW, outH, y0, y1) -> {
      float[] wx = new float[4];
      float[] wy = new float[4];
      double cx = inW / 2.0;
      double cy = inH / 2.0;
      for (int y = y0; y < y1; y++) {
        double dy = y + 0.5 - outH / 2.0;
        for (int x = 0, o = y * outW; x < outW; x++, o++) {
          double dx = x + 0.5 - outW / 2.0;
          double sx = cx + dx * cos - dy * sin - 0.5;
          double sy = cy + dx * sin + dy * cos - 0.5;
          dst[o] = sample(src, inW, inH, sx, sy, wx, wy);
        }
      }
    };
  }

  private static int sample(int[] src, int w, int h, double sx, double sy, float[] wx, float[] wy) {
    int ix = (int) Math.floor(sx);
    int iy = (int) Math.floor(sy);
    catmullRom((float) (sx - ix), wx);
    catmullRom((float) (sy - iy), wy);
    float a = 0f;
    float r = 0f;
    float g = 0f;
    float b = 0f;
    for (int j = 0; j < 4; j++) {
      int row = Math.max(0, Math.min(h - 1, iy - 1 + j)) * w;
      for (int i = 0; i < 4; i++) {
        int p = src[row + Math.max(0, Math.min(w - 1, ix - 1 + i))];
        float weight = wx[i] * wy[j];
        float pa = (p >>> 24) * weight;
        a += pa;
        r += ((p >> 16) & 0xFF) * pa;
        g += ((p >> 8) & 0xFF) * pa;
        b += (p & 0xFF) * pa;
      }
    }
    if (a <= 0f) {
      return src[Math.max(0, Math.min(h - 1, iy)) * w + Math.max(0, Math.min(w - 1, ix))]
          & 0x00FFFFFF;
    }
    return (channel(a) << 24) | (channel(r / a) << 16) | (channel(g / a) << 8) | channel(b / a);
  }

  private static int channel(float v) {
    return (int) (Math.max(0f, Math.min(255f, v)) + 0.5f);
  }

  private static void catmullRom(float t, float[] w) {
    float t2 = t * t;
    float t3 = t2 * t;
    w[0] = 0.5f * (-t3 + 2 * t2 - t);
    w[1] = 0.5f * (3 * t3 - 5 * t2 + 2);
    w[2] = 0.5f * (-3 * t3 + 4 * t2 + t);
    w[3] = 0.5f * (t3 - t2);
  }
}
