package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import java.util.Objects;

/**
 * Base para operaciones que producen la salida banda a banda (puntuales, de vecindad o de remapeo):
 * crea el destino, prepara un {@link BandKernel} una vez por llamada y deja que el {@link
 * io.github.erenriquezp.rora.core.exec.TileExecutor} reparta las bandas entre hilos.
 */
public abstract class BandOperation implements Operation {

  /** Prepara el kernel (LUT, pesos…) para estos parámetros. Sin estado mutable compartido. */
  protected abstract BandKernel prepare(Img src, Params params);

  @Override
  public final OpResult apply(Img src, Params params, OpContext ctx) {
    Objects.requireNonNull(src, "src");
    checkSchema(this, params);
    Objects.requireNonNull(ctx, "ctx");
    BandKernel kernel = prepare(src, params);
    OpKind kind = kind(params);
    int[] out = new int[src.pixelCount()];
    ctx.executor()
        .forEachBand(
            ImgAccess.pixels(src),
            out,
            src.width(),
            src.height(),
            kind.haloRows(),
            kind.rowAlign(),
            ctx.token(),
            ctx.progress(),
            kernel);
    return OpResult.of(Img.adopt(src.width(), src.height(), out));
  }

  /** Comprueba que {@code params} se construyó con el esquema de {@code op}. */
  public static void checkSchema(Operation op, Params params) {
    Objects.requireNonNull(params, "params");
    if (params.schema() != op.schema()) {
      throw new IllegalArgumentException("Los parámetros no pertenecen al esquema de " + op.id());
    }
  }
}
