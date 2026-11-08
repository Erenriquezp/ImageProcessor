package io.github.erenriquezp.rora.core.ops.blur;

import io.github.erenriquezp.rora.core.color.SrgbLut;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.ParamUnit;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.OptionalDouble;

/**
 * Desenfoque gaussiano separable en luz lineal con alpha premultiplicado, para evitar halos oscuros
 * y franjas en bordes transparentes. {@code sigma = radius / 2}; bordes por replicación. Con radio
 * 0 la salida es la entrada.
 */
public final class GaussianBlurOp extends SimpleBandOp {

  public static final String ID = "blur.gaussian";

  private static final double MAX_RADIUS = 250;

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new DecimalParam(
              "radius",
              "op.blur.gaussian.radius",
              0,
              MAX_RADIUS,
              0.1,
              4,
              OptionalDouble.of(0),
              ParamUnit.PIXELS));

  private static final OpMeta META =
      new OpMeta(
          new OpKind.Neighborhood(LinearGaussian.haloFor(MAX_RADIUS), 1),
          true,
          false,
          Cost.EXPENSIVE,
          0);

  public GaussianBlurOp() {
    super(ID, SCHEMA, META);
  }

  private static double radiusOf(Params params) {
    return Math.max(0, params.getDouble("radius"));
  }

  @Override
  public OpKind kind(Params params) {
    double radius = radiusOf(params);
    return new OpKind.Neighborhood(radius > 0 ? LinearGaussian.haloFor(radius) : 0, 1);
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    double radius = radiusOf(params);
    if (radius <= 0) {
      return (in, out, w, h, y0, y1) -> System.arraycopy(in, y0 * w, out, y0 * w, (y1 - y0) * w);
    }
    LinearGaussian gaussian = new LinearGaussian(radius);
    return (in, out, w, h, y0, y1) -> {
      float[] row = new float[4 * w];
      float[] tmp = new float[4 * w];
      for (int y = y0; y < y1; y++) {
        gaussian.premultipliedRow(in, w, h, y, row, tmp);
        for (int x = 0, c = 0, i = y * w; x < w; x++, c += 4, i++) {
          float a = row[c + 3];
          if (a <= 0f) {
            out[i] = in[i] & 0x00FFFFFF;
            continue;
          }
          int a8 = Math.min(255, (int) (a * 255f + 0.5f));
          out[i] =
              (a8 << 24)
                  | (SrgbLut.toSrgb8(row[c] / a) << 16)
                  | (SrgbLut.toSrgb8(row[c + 1] / a) << 8)
                  | SrgbLut.toSrgb8(row[c + 2] / a);
        }
      }
    };
  }
}
