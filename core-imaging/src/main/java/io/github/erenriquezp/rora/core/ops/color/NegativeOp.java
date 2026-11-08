package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;

/** {@code 255 − c} en R, G y B; alpha intacto. Port de {@code ColorFilters.negative}. */
public final class NegativeOp extends SimplePointOp {

  public NegativeOp() {
    super("color.negative", ParamSchema.EMPTY, OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int a = (p >> 24) & 0xFF;
        int r = 255 - ((p >> 16) & 0xFF);
        int g = 255 - ((p >> 8) & 0xFF);
        int b = 255 - (p & 0xFF);
        out[i] = (a << 24) | (r << 16) | (g << 8) | b;
      }
    };
  }
}
