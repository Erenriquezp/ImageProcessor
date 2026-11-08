package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.Objects;

/**
 * Base de las operaciones que cambian la geometría: la salida se calcula por bandas de filas
 * <b>destino</b>, porque su tamaño puede diferir del de la entrada.
 */
abstract class GeometryOp implements Operation {

  /** Calcula las filas {@code [y0, y1)} de la salida. */
  @FunctionalInterface
  interface RowFiller {
    void fill(int[] in, int inW, int inH, int[] out, int outW, int outH, int y0, int y1);
  }

  private final String id;
  private final ParamSchema schema;
  private final OpMeta meta;

  GeometryOp(String id, ParamSchema schema, OpMeta meta) {
    this.id = Objects.requireNonNull(id, "id");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.meta = Objects.requireNonNull(meta, "meta");
  }

  @Override
  public final String id() {
    return id;
  }

  @Override
  public final ParamSchema schema() {
    return schema;
  }

  @Override
  public final OpMeta meta() {
    return meta;
  }

  /** Prepara el cálculo de filas para estos parámetros y tamaños. */
  abstract RowFiller prepare(Params params, Size in, Size out);

  @Override
  public OpResult apply(Img src, Params params, OpContext ctx) {
    Objects.requireNonNull(src, "src");
    BandOperation.checkSchema(this, params);
    Objects.requireNonNull(ctx, "ctx");
    Size in = new Size(src.width(), src.height());
    Size size = outputSize(in, params);
    RowFiller filler = prepare(params, in, size);
    int[] input = ImgAccess.pixels(src);
    int[] out = new int[Math.multiplyExact(size.width(), size.height())];
    ctx.executor()
        .reduceBands(
            size.height(),
            ctx.token(),
            ctx.progress(),
            () -> null,
            (unused, y0, y1) ->
                filler.fill(
                    input, in.width(), in.height(), out, size.width(), size.height(), y0, y1),
            (a, b) -> null);
    return OpResult.of(Img.adopt(size.width(), size.height(), out));
  }
}
