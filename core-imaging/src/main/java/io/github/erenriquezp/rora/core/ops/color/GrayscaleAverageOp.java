package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;

/**
 * Gris por promedio entero {@code (r + g + b) / 3} (defecto conocido n.º 6: no es luminancia
 * perceptual). Port de {@code ColorFilters.grayscale}.
 */
public final class GrayscaleAverageOp extends SimplePointOp {

  public GrayscaleAverageOp() {
    super("color.grayscale-average", ParamSchema.EMPTY, OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int gray = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
        out[i] = (p & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
      }
    };
  }
}
