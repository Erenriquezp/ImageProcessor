package io.github.erenriquezp.rora.core.ops.point;

import io.github.erenriquezp.rora.core.color.Hsb;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import java.util.OptionalDouble;

/**
 * Saturación HSV: {@code s' = clamp01(s · factor)} conservando tono, valor y alpha. Port exacto de
 * {@code PointOpsFilters.pointSaturation}.
 */
public final class SaturationOp extends BandOperation {

  public static final String ID = "point.saturation";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new DecimalParam(
              "factor", "op.point.saturation.factor", 0, 2, 0.01, 1, OptionalDouble.of(1)));

  // Ida y vuelta por HSB: con factor 1 puede variar 1 nivel.
  private static final OpMeta META = OpMeta.point().withIdentityTolerance(1);

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
  protected BandKernel prepare(Img src, Params params) {
    float factor = params.getFloat("factor");
    return (in, out, w, h, y0, y1) -> {
      float[] hsv = new float[3];
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int a = (p >> 24) & 0xFF;
        Hsb.rgbToHsb((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, hsv);
        hsv[1] = PixelOps.clamp01(hsv[1] * factor);
        int rgb = Hsb.hsbToRgb(hsv[0], hsv[1], hsv[2]);
        out[i] = (a << 24) | (rgb & 0x00FFFFFF);
      }
    };
  }
}
