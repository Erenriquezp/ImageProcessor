package io.github.erenriquezp.rora.core.ops.analysis;

import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.Objects;

/**
 * Diagnóstico de exposición: cuenta los píxeles con R, G y B ≥ 240 y con R, G y B ≤ 15. Devuelve la
 * misma imagen y un {@link ExposureStats}; los porcentajes y el texto se calculan fuera del núcleo.
 * Port del recuento de {@code PointOpsFilters.diagnoseExposure}.
 */
public final class ExposureOp implements Operation {

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, true, true, Cost.CHEAP, 0);

  @Override
  public String id() {
    return "analysis.exposure";
  }

  @Override
  public ParamSchema schema() {
    return ParamSchema.EMPTY;
  }

  @Override
  public OpMeta meta() {
    return META;
  }

  @Override
  public OpResult apply(Img src, Params params, OpContext ctx) {
    Objects.requireNonNull(src, "src");
    BandOperation.checkSchema(this, params);
    Objects.requireNonNull(ctx, "ctx");
    int w = src.width();
    int[] in = ImgAccess.pixels(src);
    long[] counts =
        ctx.executor()
            .reduceBands(
                src.height(),
                ctx.token(),
                Progress.NONE,
                () -> new long[2],
                (acc, y0, y1) -> {
                  for (int i = y0 * w, end = y1 * w; i < end; i++) {
                    int p = in[i];
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    if (r >= 240 && g >= 240 && b >= 240) {
                      acc[0]++;
                    }
                    if (r <= 15 && g <= 15 && b <= 15) {
                      acc[1]++;
                    }
                  }
                },
                (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
    ctx.progress().update(1, 1);
    return OpResult.of(src, new ExposureStats(src.pixelCount(), counts[0], counts[1]));
  }
}
