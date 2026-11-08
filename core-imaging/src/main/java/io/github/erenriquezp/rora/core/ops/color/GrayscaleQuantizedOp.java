package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;
import java.util.OptionalInt;

/**
 * Gris por promedio entero, cuantizado a {@code levels} niveles. Port de {@code
 * ColorFilters.grayscaleQuantized}.
 */
public final class GrayscaleQuantizedOp extends SimplePointOp {

  public GrayscaleQuantizedOp() {
    super(
        "color.grayscale-quantized",
        ParamSchema.of(
            new IntParam(
                "levels", "op.color.grayscale-quantized.levels", 2, 64, 8, OptionalInt.empty())),
        OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    int levels = params.getInt("levels");
    // Solo hay 256 grises posibles: LUT exacta de quantize.
    int[] lut = new int[256];
    for (int v = 0; v < 256; v++) {
      lut[v] = PixelOps.quantize(v, levels);
    }
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int gray = lut[(((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3];
        out[i] = (p & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
      }
    };
  }
}
