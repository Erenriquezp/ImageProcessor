package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.color.Hsb;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.OptionalDouble;

/**
 * Escala saturación y valor HSV: {@code s' = clamp01(s · saturation)}, {@code v' = clamp01(v ·
 * value)}. Port de {@code ColorFilters.hsv}.
 */
public final class HsvOp extends SimpleBandOp {

  public HsvOp() {
    super(
        "color.hsv",
        ParamSchema.of(
            new DecimalParam(
                "saturation", "op.color.hsv.saturation", 0, 2, 0.01, 1.3, OptionalDouble.of(1)),
            new DecimalParam("value", "op.color.hsv.value", 0, 2, 0.01, 0.9, OptionalDouble.of(1))),
        OpMeta.point().withIdentityTolerance(1));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float factorS = params.getFloat("saturation");
    float factorV = params.getFloat("value");
    return (in, out, w, h, y0, y1) -> {
      float[] hsv = new float[3];
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        Hsb.rgbToHsb((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, hsv);
        float ns = PixelOps.clamp01(hsv[1] * factorS);
        float nv = PixelOps.clamp01(hsv[2] * factorV);
        int rgb = Hsb.hsbToRgb(hsv[0], ns, nv);
        out[i] = (p & 0xFF000000) | (rgb & 0x00FFFFFF);
      }
    };
  }
}
