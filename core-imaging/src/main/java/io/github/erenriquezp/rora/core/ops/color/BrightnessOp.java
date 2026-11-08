package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.OptionalInt;

/**
 * Suma constante con recorte: {@code clamp(c + value)}. Port de {@code ColorFilters.brightness}.
 */
public final class BrightnessOp extends SimpleBandOp {

  public BrightnessOp() {
    super(
        "color.brightness",
        ParamSchema.of(
            new IntParam("value", "op.color.brightness.value", -100, 100, 40, OptionalInt.of(0))),
        OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int value = params.getInt("value");
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = PixelOps.clamp(((p >> 16) & 0xFF) + value);
        int g = PixelOps.clamp(((p >> 8) & 0xFF) + value);
        int b = PixelOps.clamp((p & 0xFF) + value);
        out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
      }
    };
  }
}
