package io.github.erenriquezp.rora.core.ops.tone;

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
 * Posterizado de R, G y B a {@code levels} niveles ({@code ≥ 256} = identidad). Port de {@code
 * ArtisticFilters.retro1}.
 */
public final class PosterizeOp extends SimpleBandOp {

  public PosterizeOp() {
    super(
        "tone.posterize",
        ParamSchema.of(
            new IntParam("levels", "op.tone.posterize.levels", 2, 256, 4, OptionalInt.of(256))),
        OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int[] lut = quantizeLut(params.getInt("levels"));
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        out[i] =
            (p & 0xFF000000)
                | (lut[(p >> 16) & 0xFF] << 16)
                | (lut[(p >> 8) & 0xFF] << 8)
                | lut[p & 0xFF];
      }
    };
  }

  /**
   * {@code quantize(v, levels)} para los 256 valores posibles (resultado idéntico, sin repetir).
   */
  static int[] quantizeLut(int levels) {
    int[] lut = new int[256];
    for (int v = 0; v < 256; v++) {
      lut[v] = PixelOps.quantize(v, levels);
    }
    return lut;
  }
}
