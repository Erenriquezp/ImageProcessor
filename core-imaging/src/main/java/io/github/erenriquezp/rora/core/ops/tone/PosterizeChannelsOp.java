package io.github.erenriquezp.rora.core.ops.tone;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Posterizado solo de los canales seleccionados. Port de {@code ArtisticFilters.retro2}. Con 255
 * niveles <b>no</b> es identidad (paso 255/254).
 */
public final class PosterizeChannelsOp extends SimpleBandOp {

  public PosterizeChannelsOp() {
    super(
        "tone.posterize-channels",
        ParamSchema.of(
            new IntParam(
                "levels", "op.tone.posterize-channels.levels", 2, 255, 4, OptionalInt.of(256)),
            new SelectionParam(
                "channels",
                "op.tone.posterize-channels.channels",
                List.of("r", "g", "b"),
                Set.of("r", "g", "b"),
                Optional.of(Set.of()))),
        OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int[] lut = PosterizeOp.quantizeLut(params.getInt("levels"));
    Set<String> ch = params.getSelection("channels");
    boolean qr = ch.contains("r");
    boolean qg = ch.contains("g");
    boolean qb = ch.contains("b");
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        int nr = qr ? lut[r] : r;
        int ng = qg ? lut[g] : g;
        int nb = qb ? lut[b] : b;
        out[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
      }
    };
  }
}
