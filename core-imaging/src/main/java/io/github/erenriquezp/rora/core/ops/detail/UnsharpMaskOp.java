package io.github.erenriquezp.rora.core.ops.detail;

import io.github.erenriquezp.rora.core.color.SrgbLut;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.ParamUnit;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.LinearPack;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import io.github.erenriquezp.rora.core.ops.blur.LinearGaussian;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Máscara de enfoque sobre la luminancia lineal: {@code Y' = Y + amount · (Y - Y desenfocada)}. Se
 * aplica como ganancia sobre RGB, de modo que no crea halos de color. El umbral compara en 8 bits
 * sRGB y deja intactas las diferencias pequeñas (ruido). Con {@code amount = 0}, la identidad.
 */
public final class UnsharpMaskOp extends SimpleBandOp {

  public static final String ID = "detail.unsharp-mask";

  private static final double MAX_RADIUS = 250;
  private static final float MAX_GAIN = 16f;

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new DecimalParam(
              "amount", "op.detail.unsharp-mask.amount", 0, 300, 1, 100, OptionalDouble.of(0)),
          new DecimalParam(
              "radius",
              "op.detail.unsharp-mask.radius",
              0.3,
              MAX_RADIUS,
              0.1,
              1.5,
              OptionalDouble.of(1.5),
              ParamUnit.PIXELS),
          new IntParam(
              "threshold", "op.detail.unsharp-mask.threshold", 0, 255, 0, OptionalInt.of(0)));

  private static final OpMeta META =
      new OpMeta(
          new OpKind.Neighborhood(LinearGaussian.haloFor(MAX_RADIUS), 1),
          true,
          true,
          Cost.EXPENSIVE,
          0);

  public UnsharpMaskOp() {
    super(ID, SCHEMA, META);
  }

  private static double radiusOf(Params params) {
    return Math.max(0.1, params.getDouble("radius"));
  }

  @Override
  public OpKind kind(Params params) {
    return params.getDouble("amount") > 0
        ? new OpKind.Neighborhood(LinearGaussian.haloFor(radiusOf(params)), 1)
        : new OpKind.Neighborhood(0, 1);
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float amount = (float) (params.getDouble("amount") / 100);
    if (amount <= 0f) {
      return (in, out, w, h, y0, y1) -> System.arraycopy(in, y0 * w, out, y0 * w, (y1 - y0) * w);
    }
    int threshold = params.getInt("threshold");
    LinearGaussian gaussian = new LinearGaussian(radiusOf(params));
    return (in, out, w, h, y0, y1) -> {
      float[] blurred = new float[w];
      float[] tmp = new float[w];
      for (int y = y0; y < y1; y++) {
        gaussian.lumaRow(in, w, h, y, blurred, tmp);
        for (int x = 0, i = y * w; x < w; x++, i++) {
          int p = in[i];
          float r = SrgbLut.toLinear((p >> 16) & 0xFF);
          float g = SrgbLut.toLinear((p >> 8) & 0xFF);
          float b = SrgbLut.toLinear(p & 0xFF);
          float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
          float base = blurred[x];
          if (threshold > 0
              && Math.abs(SrgbLut.toSrgb8(luma) - SrgbLut.toSrgb8(base)) < threshold) {
            out[i] = p;
            continue;
          }
          float target = Math.max(0f, luma + amount * (luma - base));
          float gain = luma > 0f ? Math.min(target / luma, MAX_GAIN) : 1f;
          float lift = target - gain * luma;
          r = r * gain + lift;
          g = g * gain + lift;
          b = b * gain + lift;
          out[i] = LinearPack.pack(p & 0xFF000000, r, g, b, target);
        }
      }
    };
  }
}
