package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import java.util.List;
import java.util.Objects;

/**
 * Aplica una cadena de operaciones puntuales en <b>una sola pasada por bandas</b>.
 *
 * <p>Por cada banda de filas se ejecutan los kernels de las etapas uno tras otro, en el orden de la
 * cadena, cada uno sobre las filas de esa banda que escribió la etapa anterior (búferes ping-pong
 * de tamaño completo). Es exacta por construcción: cada kernel es el mismo que usa {@code apply} de
 * su operación y, al ser puntual, la banda {@code [y0, y1)} de su salida solo depende de la misma
 * banda de su entrada, que ya está completa cuando se ejecuta. Lo que se gana es localidad de caché
 * (los datos de la banda siguen en caché entre etapas) y una sola distribución de bandas entre
 * hilos en lugar de una por etapa.
 */
public final class PointFusion {

  /** Una etapa: operación puntual y sus parámetros (con el esquema de esa operación). */
  public record Stage(PointOperation op, Params params) {
    public Stage {
      Objects.requireNonNull(op, "op");
      BandOperation.checkSchema(op, params);
      if (!(op.kind(params) instanceof OpKind.Point)) {
        throw new IllegalArgumentException(op.id() + " no es puntual con estos parámetros");
      }
    }
  }

  private PointFusion() {}

  /** Resultado de aplicar {@code stages} en orden sobre {@code src}; {@code src} no cambia. */
  public static Img apply(Img src, List<Stage> stages, OpContext ctx) {
    Objects.requireNonNull(src, "src");
    Objects.requireNonNull(ctx, "ctx");
    if (stages.isEmpty()) {
      throw new IllegalArgumentException("Cadena vacía");
    }
    int w = src.width();
    int h = src.height();
    int k = stages.size();
    BandKernel[] kernels = new BandKernel[k];
    for (int j = 0; j < k; j++) {
      Stage s = stages.get(j);
      kernels[j] = s.op().pointKernel(s.params(), w, h);
    }
    int[] out = new int[src.pixelCount()];
    int[] tmp = k > 1 ? new int[src.pixelCount()] : null;
    // La etapa j escribe en out si (k-1-j) es par: así la última siempre escribe en out.
    BandKernel fused =
        (in, dst, width, height, y0, y1) -> {
          int[] cur = in;
          for (int j = 0; j < k; j++) {
            int[] next = ((k - 1 - j) & 1) == 0 ? dst : tmp;
            kernels[j].run(cur, next, width, height, y0, y1);
            cur = next;
          }
        };
    ctx.executor()
        .forEachBand(ImgAccess.pixels(src), out, w, h, 0, 1, ctx.token(), ctx.progress(), fused);
    return Img.adopt(w, h, out);
  }
}
