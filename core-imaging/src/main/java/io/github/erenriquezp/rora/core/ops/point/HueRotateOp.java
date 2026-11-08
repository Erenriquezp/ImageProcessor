package io.github.erenriquezp.rora.core.ops.point;

import io.github.erenriquezp.rora.core.color.Hsb;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.OptionalDouble;

/**
 * Desplaza el tono HSV {@code degrees/360} (módulo 1). Port de {@code PointOpsFilters.hueRotate}.
 */
public final class HueRotateOp extends SimpleBandOp {

  public HueRotateOp() {
    super(
        "point.hue-rotate",
        ParamSchema.of(
            new DecimalParam(
                "degrees", "op.point.hue-rotate.degrees", -180, 180, 1, 30, OptionalDouble.of(0))),
        OpMeta.point().withIdentityTolerance(1));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float delta = params.getFloat("degrees") / 360f;
    return (in, out, w, h, y0, y1) -> {
      float[] hsv = new float[3];
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        Hsb.rgbToHsb((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, hsv);
        float nh = hsv[0] + delta;
        nh = nh - (float) Math.floor(nh);
        int rgb = Hsb.hsbToRgb(nh, hsv[1], hsv[2]);
        out[i] = (p & 0xFF000000) | (rgb & 0x00FFFFFF);
      }
    };
  }
}
