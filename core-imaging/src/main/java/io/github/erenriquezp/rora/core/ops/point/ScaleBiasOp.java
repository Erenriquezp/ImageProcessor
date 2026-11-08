package io.github.erenriquezp.rora.core.ops.point;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;
import java.util.OptionalDouble;

/** {@code clamp(round(c · scale + bias))} en {@code float}. Port de {@code scaleBias}. */
public final class ScaleBiasOp extends SimplePointOp {

  public ScaleBiasOp() {
    super(
        "point.scale-bias",
        ParamSchema.of(
            new DecimalParam(
                "scale", "op.point.scale-bias.scale", 0, 3, 0.01, 1, OptionalDouble.of(1)),
            new DecimalParam(
                "bias", "op.point.scale-bias.bias", -128, 128, 1, 0, OptionalDouble.of(0))),
        OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    float scale = params.getFloat("scale");
    float bias = params.getFloat("bias");
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = PixelOps.clamp(Math.round(((p >> 16) & 0xFF) * scale + bias));
        int g = PixelOps.clamp(Math.round(((p >> 8) & 0xFF) * scale + bias));
        int b = PixelOps.clamp(Math.round((p & 0xFF) * scale + bias));
        out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
      }
    };
  }
}
