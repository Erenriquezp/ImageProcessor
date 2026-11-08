package io.github.erenriquezp.rora.core.ops.point;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;
import java.util.OptionalInt;

/**
 * Umbral sobre la luminancia BT.709 redondeada; con {@code softWidth > 0}, rampa lineal de ancho
 * {@code 2·softWidth}. Port de {@code PointOpsFilters.pointThreshold}.
 */
public final class SoftThresholdOp extends SimplePointOp {

  public SoftThresholdOp() {
    super(
        "point.soft-threshold",
        ParamSchema.of(
            new IntParam(
                "threshold", "op.point.soft-threshold.threshold", 0, 255, 128, OptionalInt.empty()),
            new IntParam(
                "softWidth", "op.point.soft-threshold.softWidth", 0, 64, 0, OptionalInt.empty())),
        OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    int thr = PixelOps.clamp(params.getInt("threshold"));
    int soft = Math.max(0, params.getInt("softWidth"));
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int lum = Math.round(PixelOps.luminance709((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
        int v;
        if (soft <= 0) {
          v = lum >= thr ? 255 : 0;
        } else {
          float t = (lum - (thr - soft)) / (float) (2 * soft);
          v = PixelOps.clamp(Math.round(PixelOps.clamp01(t) * 255));
        }
        out[i] = (p & 0xFF000000) | (v << 16) | (v << 8) | v;
      }
    };
  }
}
