package io.github.erenriquezp.rora.app.session.wallpaper;

import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import io.github.erenriquezp.rora.wallpapers.render.TargetRenderer;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Trabajo de la herramienta Wallpapers fuera del hilo FX: análisis de rostros sobre el resultado
 * FULL de la receta (en caché por receta), miniaturas aproximadas sobre el proxy y la exportación.
 * Nunca ejecuta la mejora con IA.
 */
public final class WallpaperWorkspace implements AutoCloseable {

  /** Rostros del resultado de la receta. */
  public record Analysis(int width, int height, List<Subject> subjects, boolean detectorFailed) {
    public Analysis {
      subjects = List.copyOf(subjects);
    }
  }

  /** Avisos de la exportación, siempre en el hilo de {@code publisher}. */
  public interface ExportListener {
    /** Empieza el destino {@code index} de {@code total}. */
    void started(int index, int total, String name);

    /** Avance global en {@code [0, 1]}. */
    void progress(double fraction);

    void done(WallpaperResult result);

    /** No se pudo ni empezar (p. ej., el render FULL falló). */
    void failed(String message);
  }

  private final Supplier<SubjectDetector> detectors;
  private final TileExecutor executor;
  private final boolean ownsExecutor;
  private SubjectDetector detector;
  private Object cachedKey;
  private Analysis cached;

  /**
   * @param detectors crea el detector la primera vez que hace falta
   * @param executor hilos para renderizar los destinos
   */
  public WallpaperWorkspace(Supplier<SubjectDetector> detectors, TileExecutor executor) {
    this(detectors, executor, false);
  }

  private WallpaperWorkspace(
      Supplier<SubjectDetector> detectors, TileExecutor executor, boolean ownsExecutor) {
    this.detectors = Objects.requireNonNull(detectors, "detectors");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.ownsExecutor = ownsExecutor;
  }

  /** Con YuNet incluido y un hilo menos que núcleos para renderizar (la aplicación). */
  public static WallpaperWorkspace withBundledDetector() {
    return new WallpaperWorkspace(
        io.github.erenriquezp.rora.app.session.ai.FaceDetectors::bundled,
        TileExecutor.create(TileExecutor.defaultParallelism()),
        true);
  }

  /** Sin detector de rostros y en el hilo que llama (tests y entornos sin IA). */
  public static WallpaperWorkspace withoutDetector() {
    return new WallpaperWorkspace(SubjectDetector::none, TileExecutor.sequential());
  }

  private record Key(EditRecipe recipe, Optional<OpSpec> pending, boolean detect) {}

  /**
   * Analiza el resultado actual de la receta. Con la misma receta responde de la caché sin
   * renderizar. {@code done} llega por {@code publisher}.
   *
   * @param detect si se buscan rostros (A1: el usuario puede desactivarlo)
   */
  public void analyze(
      EditSession<?> session,
      boolean detect,
      Executor background,
      Executor publisher,
      Consumer<Analysis> done) {
    Key key = new Key(session.recipe(), session.pending(), detect);
    if (key.equals(cachedKey) && cached != null) {
      done.accept(cached);
      return;
    }
    session.exportImg(
        image ->
            background.execute(
                () -> {
                  Analysis a = analyzeNow(image, detect);
                  publisher.execute(
                      () -> {
                        cachedKey = key;
                        cached = a;
                        done.accept(a);
                      });
                }),
        failure -> done.accept(null));
  }

  /** Detección síncrona (hilo de trabajo). Un fallo del detector deja la lista vacía. */
  Analysis analyzeNow(Img image, boolean detect) {
    if (!detect) {
      return new Analysis(image.width(), image.height(), List.of(), false);
    }
    try {
      synchronized (this) {
        if (detector == null) {
          detector = detectors.get();
        }
      }
      List<Subject> s = detector.detect(image, CancellationToken.none());
      return new Analysis(image.width(), image.height(), s, false);
    } catch (Exception | LinkageError e) {
      return new Analysis(image.width(), image.height(), List.of(), true);
    }
  }

  /**
   * Miniaturas aproximadas de todos los destinos a partir del proxy: el mismo encuadre normalizado,
   * con el lado mayor en {@code maxSide}. Sin tramado. Hilo de trabajo.
   */
  public static List<Img> thumbnails(Img preview, WallpaperPlan plan, int maxSide) {
    Img src = TargetRenderer.opaque(preview, 0xFFFFFFFF);
    int pw = src.width();
    int ph = src.height();
    List<Img> out = new ArrayList<>();
    for (PlannedTarget p : plan.targets()) {
      Target t = p.target();
      double k = Math.min(1.0, (double) maxSide / Math.max(t.width(), t.height()));
      int w = (int) Math.max(1, Math.round(t.width() * k));
      int h = (int) Math.max(1, Math.round(t.height() * k));
      FrameRect frame;
      IntRect content;
      if (p.filled()) {
        frame = new FrameRect(0, 0, pw, ph);
        content = Fit.contain(pw, ph, w, h);
      } else {
        FrameRect f =
            p.frame().normalized(plan.sourceWidth(), plan.sourceHeight()).denormalized(pw, ph);
        // El redondeo puede dejar el lado una fracción por encima del proxy.
        frame =
            new FrameRect(f.x(), f.y(), Math.min(f.w(), pw), Math.min(f.h(), ph))
                .clampedInto(pw, ph);
        content = new IntRect(0, 0, w, h);
      }
      FillSpec fill = t.fill();
      out.add(
          TargetRenderer.render(
              src,
              new TargetRenderer.Job(w, h, frame, content, fill, false),
              new float[3],
              TileExecutor.sequential(),
              CancellationToken.none(),
              Progress.NONE));
    }
    return out;
  }

  /**
   * Exporta la tanda sobre el resultado FULL de la receta. Devuelve el ticket del render; {@code
   * token} cancela también la escritura.
   */
  public RenderLane.Ticket export(
      EditSession<?> session,
      WallpaperPlan plan,
      List<WallpaperExport.Item> items,
      WallpaperExport.Settings settings,
      ImageMetadata metadata,
      Optional<ProvenanceXmp.Provenance> provenance,
      CancellationToken token,
      Executor io,
      Executor publisher,
      ExportListener listener) {
    int total = plan.targets().size();
    return session.exportImg(
        image ->
            io.execute(
                () -> {
                  WallpaperResult r;
                  try {
                    r =
                        WallpaperExport.run(
                            image,
                            metadata,
                            provenance,
                            plan,
                            items,
                            settings,
                            executor,
                            token,
                            new WallpaperJob.Listener() {
                              @Override
                              public void started(int i, int n, PlannedTarget t) {
                                publisher.execute(
                                    () -> {
                                      listener.started(i, n, t.target().name());
                                      listener.progress((double) i / Math.max(1, n));
                                    });
                              }

                              @Override
                              public void progress(int i, double f) {
                                double overall = (i + f) / Math.max(1, total);
                                publisher.execute(() -> listener.progress(overall));
                              }
                            });
                  } catch (RuntimeException e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    publisher.execute(() -> listener.failed(msg));
                    return;
                  }
                  WallpaperResult result = r;
                  publisher.execute(() -> listener.done(result));
                }),
        failure -> listener.failed(failure.message()));
  }

  @Override
  public synchronized void close() {
    if (detector != null) {
      detector.close();
      detector = null;
    }
    if (ownsExecutor) {
      executor.close();
    }
  }
}
