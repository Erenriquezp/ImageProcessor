package io.github.erenriquezp.rora.core.ops.point;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;

/** Gris BT.709 {@code clamp(round(Y))} en {@code float}. Port de {@code toLuminance}. */
public final class LuminanceOp extends SimpleBandOp {

  public LuminanceOp() {
    super("point.luminance", ParamSchema.EMPTY, OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int y =
            PixelOps.clamp(
                Math.round(PixelOps.luminance709((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF)));
        out[i] = (p & 0xFF000000) | (y << 16) | (y << 8) | y;
      }
    };
  }
}
