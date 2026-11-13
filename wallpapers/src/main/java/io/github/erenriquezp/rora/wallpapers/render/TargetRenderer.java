package io.github.erenriquezp.rora.wallpapers.render;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.AlphaFlatten;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import java.util.Objects;

/** Renderiza un destino: exactamente {@code width × height}, opaco y determinista. */
public final class TargetRenderer {

  private TargetRenderer() {}

  /**
   * Qué pintar.
   *
   * @param frame encuadre en píxeles de la fuente (en "Contener", la fuente entera)
   * @param content dónde va el encuadre en la salida (toda la salida en "Cubrir")
   * @param fill relleno de lo que queda fuera de {@code content}
   * @param dither tramar el relleno suave (solo tiene sentido con PNG)
   */
  public record Job(
      int width, int height, FrameRect frame, IntRect content, FillSpec fill, boolean dither) {
    public Job {
      Objects.requireNonNull(frame, "frame");
      Objects.requireNonNull(content, "content");
      Objects.requireNonNull(fill, "fill");
      if (width < 1
          || height < 1
          || content.right() > width
          || content.bottom() > height
          || content.x() < 0
          || content.y() < 0) {
        throw new IllegalArgumentException("el contenido no cabe en la salida");
      }
    }

    /** Si el contenido no cubre toda la salida. */
    public boolean filled() {
      return content.w() != width || content.h() != height;
    }
  }

  /**
   * La fuente opaca: si tiene píxeles con alfa, una copia aplanada sobre {@code background} (regla
   * de la fase 4); si no, la misma imagen.
   */
  public static Img opaque(Img source, int background) {
    // Se mira fila a fila para no copiar la fuente entera cuando ya es opaca (lo habitual).
    int[] row = new int[source.width()];
    boolean translucent = false;
    for (int y = 0; y < source.height() && !translucent; y++) {
      source.copyRow(y, row, 0);
      for (int p : row) {
        if (p >>> 24 != 0xFF) {
          translucent = true;
          break;
        }
      }
    }
    if (!translucent) {
      return source;
    }
    int[] px = source.toArray();
    AlphaFlatten.flatten(px, background);
    return Img.adopt(source.width(), source.height(), px);
  }

  /**
   * @param source fuente opaca (ver {@link #opaque})
   * @param dominant color dominante en lineal; solo se usa con el degradado
   */
  public static Img render(
      Img source,
      Job job,
      float[] dominant,
      TileExecutor executor,
      CancellationToken token,
      Progress progress) {
    int w = job.width();
    int h = job.height();
    int[] out = new int[w * h];
    if (job.filled()) {
      Fills.background(source, job.fill(), dominant, w, h, out, job.dither(), executor, token);
    }
    Resampler.resampleInto(source, job.frame(), job.content(), out, w, executor, token, progress);
    if (job.filled() && job.fill().kind() == FillSpec.Kind.MIRROR) {
      Fills.mirror(out, w, h, job.content(), job.fill().strength(), job.dither());
    }
    return Img.adopt(w, h, out);
  }
}
