package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.OptionalInt;

/**
 * Blanco si el promedio entero {@code (r + g + b) / 3} es {@code > threshold}, si no negro. Port de
 * {@code ColorFilters.bwThreshold}.
 */
public final class ThresholdBwOp extends SimpleBandOp {

  public ThresholdBwOp() {
    super(
        "color.threshold-bw",
        ParamSchema.of(
            new IntParam(
                "threshold", "op.color.threshold-bw.threshold", 0, 255, 127, OptionalInt.empty())),
        OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int threshold = params.getInt("threshold");
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int gray = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
        int c = gray > threshold ? 255 : 0;
        out[i] = (p & 0xFF000000) | (c << 16) | (c << 8) | c;
      }
    };
  }
}
