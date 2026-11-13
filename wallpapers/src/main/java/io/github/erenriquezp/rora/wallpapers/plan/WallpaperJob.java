package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.wallpapers.render.DominantColor;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import io.github.erenriquezp.rora.wallpapers.render.TargetRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exporta una tanda: destino a destino, con un solo destino en memoria a la vez. Un fallo no
 * detiene a los demás; la cancelación sí (lo pendiente queda como cancelado).
 */
public final class WallpaperJob {

  /** Progreso de la tanda. Se llama desde el hilo de trabajo. */
  public interface Listener {
    Listener NONE = new Listener() {};

    /** Empieza el destino {@code index} (desde 0) de {@code total}. */
    default void started(int index, int total, PlannedTarget target) {}

    /** Progreso dentro del destino actual, en {@code [0, 1]}. */
    default void progress(int index, double fraction) {}

    default void finished(int index, WallpaperResult.Outcome outcome) {}
  }

  private WallpaperJob() {}

  /**
   * @param source resultado FULL de la receta, del tamaño del plan
   * @param background color sobre el que se aplana el alfa (el de la exportación)
   */
  public static WallpaperResult run(
      Img source,
      WallpaperPlan plan,
      OutputFormat format,
      int background,
      TargetSink sink,
      TileExecutor executor,
      CancellationToken token,
      Listener listener) {
    Objects.requireNonNull(sink, "sink");
    if (source.width() != plan.sourceWidth() || source.height() != plan.sourceHeight()) {
      throw new IllegalArgumentException("la fuente no es la del plan");
    }
    List<PlannedTarget> targets = plan.targets();
    List<WallpaperResult.Outcome> out = new ArrayList<>();
    Img opaque = null;
    float[] dominant = null;
    int total = targets.size();
    for (int i = 0; i < total; i++) {
      PlannedTarget t = targets.get(i);
      if (token.isCancelled()) {
        cancelRest(targets, i, out, listener);
        break;
      }
      listener.started(i, total, t);
      WallpaperResult.Outcome o;
      try {
        if (opaque == null) {
          opaque = TargetRenderer.opaque(source, background);
        }
        if (dominant == null && t.filled() && t.target().fill().kind() == FillSpec.Kind.GRADIENT) {
          dominant = DominantColor.of(opaque, token);
        }
        FormatId f = format.resolve(t);
        int index = i;
        Progress p = (done, all) -> listener.progress(index, 0.7 * done / all);
        Img img =
            TargetRenderer.render(
                opaque,
                new TargetRenderer.Job(
                    t.target().width(),
                    t.target().height(),
                    t.frame(),
                    t.content(),
                    t.target().fill(),
                    OutputFormat.dithers(f)),
                dominant == null ? new float[3] : dominant,
                executor,
                token,
                p);
        TargetSink.Written w = sink.write(t, img, f, token);
        o = new WallpaperResult.Written(t.target(), w.file(), w.bytes());
      } catch (CancelledException e) {
        cancelRest(targets, i, out, listener);
        break;
      } catch (Exception e) {
        o = new WallpaperResult.Failed(t.target(), describe(e));
      } catch (OutOfMemoryError e) {
        o = new WallpaperResult.Failed(t.target(), "sin memoria");
      }
      out.add(o);
      listener.finished(i, o);
    }
    return new WallpaperResult(out);
  }

  private static void cancelRest(
      List<PlannedTarget> targets, int from, List<WallpaperResult.Outcome> out, Listener l) {
    for (int j = from; j < targets.size(); j++) {
      WallpaperResult.Outcome c = new WallpaperResult.Cancelled(targets.get(j).target());
      out.add(c);
      l.finished(j, c);
    }
  }

  private static String describe(Exception e) {
    String m = e.getMessage();
    return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
  }
}
