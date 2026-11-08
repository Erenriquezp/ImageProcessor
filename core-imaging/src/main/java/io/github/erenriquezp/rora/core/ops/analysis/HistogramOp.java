package io.github.erenriquezp.rora.core.ops.analysis;

import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.ChannelHistogram;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.Arrays;
import java.util.Objects;

/**
 * Histograma de 256 niveles por canal R, G y B. Devuelve la misma imagen y un {@link
 * ChannelHistogram}; el dibujo del gráfico queda fuera del núcleo. Port del recuento de {@code
 * HistogramService.generateHistogram}.
 */
public final class HistogramOp implements Operation {

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, true, true, Cost.CHEAP, 0);

  @Override
  public String id() {
    return "analysis.histogram";
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
    int[] hist =
        ctx.executor()
            .reduceBands(
                src.height(),
                ctx.token(),
                Progress.NONE,
                () -> new int[768],
                (acc, y0, y1) -> {
                  for (int i = y0 * w, end = y1 * w; i < end; i++) {
                    int p = in[i];
                    acc[(p >> 16) & 0xFF]++;
                    acc[256 + ((p >> 8) & 0xFF)]++;
                    acc[512 + (p & 0xFF)]++;
                  }
                },
                (a, b) -> {
                  for (int i = 0; i < a.length; i++) {
                    a[i] += b[i];
                  }
                  return a;
                });
    ctx.progress().update(1, 1);
    return OpResult.of(
        src,
        new ChannelHistogram(
            Arrays.copyOfRange(hist, 0, 256),
            Arrays.copyOfRange(hist, 256, 512),
            Arrays.copyOfRange(hist, 512, 768)));
  }
}
