package io.github.erenriquezp.rora.core.ops.point;

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
 * Ganancia por canal: {@code clamp(round(c · gain))} en {@code float}. Port de {@code colorAdjust}.
 */
public final class ColorGainOp extends SimpleBandOp {

  public ColorGainOp() {
    super(
        "point.color-gain",
        ParamSchema.of(gain("gainR"), gain("gainG"), gain("gainB")),
        OpMeta.point());
  }

  private static DecimalParam gain(String key) {
    return new DecimalParam(key, "op.point.color-gain." + key, 0, 2, 0.01, 1, OptionalDouble.of(1));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float gainR = params.getFloat("gainR");
    float gainG = params.getFloat("gainG");
    float gainB = params.getFloat("gainB");
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = PixelOps.clamp(Math.round(((p >> 16) & 0xFF) * gainR));
        int g = PixelOps.clamp(Math.round(((p >> 8) & 0xFF) * gainG));
        int b = PixelOps.clamp(Math.round((p & 0xFF) * gainB));
        out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
      }
    };
  }
}
