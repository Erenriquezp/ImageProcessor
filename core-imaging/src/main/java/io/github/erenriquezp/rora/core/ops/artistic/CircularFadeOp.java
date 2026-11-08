package io.github.erenriquezp.rora.core.ops.artistic;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;

/**
 * Viñeta radial <b>en el alpha</b>: {@code a = (int)(255 · max(0, 1 − d/dMax))} con el centro en
 * {@code (w/2, h/2)}; RGB intacto. Port de {@code ArtisticFilters.circularFade}.
 */
public final class CircularFadeOp extends SimplePointOp {

  public CircularFadeOp() {
    super(
        "artistic.circular-fade",
        ParamSchema.EMPTY,
        OpMeta.point().withKind(OpKind.POSITIONAL).withAlphaChanged());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    return (in, out, w, h, y0, y1) -> {
      double cx = w / 2.0;
      double cy = h / 2.0;
      double distMax = Math.sqrt(cx * cx + cy * cy);
      for (int y = y0; y < y1; y++) {
        double dy = y - cy;
        for (int x = 0, i = y * w; x < w; x++, i++) {
          double dx = x - cx;
          double dist = Math.sqrt(dx * dx + dy * dy);
          double factor = Math.max(0.0, 1.0 - (dist / distMax));
          int a = (int) (255 * factor);
          out[i] = (a << 24) | (in[i] & 0x00FFFFFF);
        }
      }
    };
  }
}
