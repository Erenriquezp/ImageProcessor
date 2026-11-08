package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;
import java.util.OptionalInt;

/**
 * Duotono: luminancia BT.709 en {@code double} multiplicada por el tinte, {@code c' =
 * clamp(round(lum · tinte / 255))}. Port de {@code ArtisticFilters.recolor}.
 */
public final class RecolorOp extends SimplePointOp {

  public RecolorOp() {
    super(
        "color.recolor",
        ParamSchema.of(
            new ColorParam(
                "tone", "op.color.recolor.tone", 0xFFC87850, false, OptionalInt.empty())),
        OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    int tone = params.getColor("tone");
    int toneR = (tone >> 16) & 0xFF;
    int toneG = (tone >> 8) & 0xFF;
    int toneB = tone & 0xFF;
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        int nr = PixelOps.clamp((int) Math.round((lum * toneR) / 255.0));
        int ng = PixelOps.clamp((int) Math.round((lum * toneG) / 255.0));
        int nb = PixelOps.clamp((int) Math.round((lum * toneB) / 255.0));
        out[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
      }
    };
  }
}
