package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dos carriles de render independientes (sin JavaFX):
 *
 * <ul>
 *   <li><b>PREVIEW</b>: hilo {@code rora-preview}, pool de bandas propio de N−1 hilos, debounce de
 *       30 ms, último gana.
 *   <li><b>FULL</b>: hilo {@code rora-full} con prioridad menor, pool propio de max(1, (N−1)/2)
 *       hilos, sin debounce; último gana entre interactivas, exportaciones protegidas.
 * </ul>
 *
 * Un paso legacy no cancelable en FULL ocupa solo el carril FULL: nunca bloquea un preview.
 */
public final class RenderCoordinator implements AutoCloseable {

  /** Debounce del preview: ver {@code .planning/FASE2_diseno.md} §10.2. */
  public static final long PREVIEW_DEBOUNCE_NANOS = 30_000_000L;

  private final RenderLane preview;
  private final RenderLane full;
  private final RenderLane aux;
  private final AutoCloseable resources;

  /** Para tests: carriles ya construidos (con ejecutores y tiempo manuales). */
  public RenderCoordinator(RenderLane preview, RenderLane full, AutoCloseable resources) {
    this(preview, full, full, resources);
  }

  /** Para tests: con carril auxiliar propio (imagen original del modo comparar). */
  public RenderCoordinator(
      RenderLane preview, RenderLane full, RenderLane aux, AutoCloseable resources) {
    this.preview = Objects.requireNonNull(preview, "preview");
    this.full = Objects.requireNonNull(full, "full");
    this.aux = Objects.requireNonNull(aux, "aux");
    this.resources = resources;
  }

  /** Coordinador real con sus hilos y pools; {@code publisher} = {@code Platform::runLater}. */
  public static RenderCoordinator create(
      Renderer renderer, java.util.concurrent.Executor publisher) {
    int n = TileExecutor.defaultParallelism();
    TileExecutor previewTiles = TileExecutor.create(n);
    TileExecutor fullTiles = TileExecutor.create(Math.max(1, n / 2));
    ExecutorService previewWorker = singleThread("rora-preview", Thread.NORM_PRIORITY);
    ExecutorService fullWorker = singleThread("rora-full", Thread.NORM_PRIORITY - 1);
    ExecutorService auxWorker = singleThread("rora-aux", Thread.NORM_PRIORITY);
    Scheduler scheduler = Scheduler.system();
    RenderLane p =
        new RenderLane(
            "preview",
            renderer,
            previewTiles,
            previewWorker,
            scheduler,
            publisher,
            PREVIEW_DEBOUNCE_NANOS);
    RenderLane f = new RenderLane("full", renderer, fullTiles, fullWorker, scheduler, publisher, 0);
    RenderLane x = new RenderLane("aux", renderer, previewTiles, auxWorker, scheduler, publisher, 0);
    AutoCloseable res =
        () -> {
          previewWorker.shutdownNow();
          fullWorker.shutdownNow();
          auxWorker.shutdownNow();
          awaitQuietly(previewWorker);
          awaitQuietly(fullWorker);
          awaitQuietly(auxWorker);
          previewTiles.close();
          fullTiles.close();
        };
    return new RenderCoordinator(p, f, x, res);
  }

  static ExecutorService singleThread(String name, int priority) {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread t = new Thread(r, name);
          t.setDaemon(true);
          t.setPriority(priority);
          return t;
        });
  }

  private static void awaitQuietly(ExecutorService e) {
    try {
      // Un paso legacy no se puede interrumpir: se espera poco; los hilos son daemon.
      e.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  public RenderLane.Ticket preview(Renderer.Request req, RenderLane.Listener l) {
    return preview.submit(req, l, false);
  }

  /** FULL interactivo, último gana dentro de su propósito ({@code slot}). */
  public RenderLane.Ticket full(
      Renderer.Request req, RenderLane.Listener l, RenderLane.Slot slot) {
    return full.submit(req, l, slot);
  }

  /** FULL interactivo en la ranura por defecto. */
  public RenderLane.Ticket full(Renderer.Request req, RenderLane.Listener l) {
    return full.submit(req, l, RenderLane.Slot.DEFAULT);
  }

  /** Render barato de la imagen original (modo comparar), sin competir con los otros carriles. */
  public RenderLane.Ticket aux(Renderer.Request req, RenderLane.Listener l) {
    return aux.submit(req, l, RenderLane.Slot.DEFAULT);
  }

  /** FULL de exportación: no lo sustituye ninguna petición interactiva. */
  public RenderLane.Ticket export(Renderer.Request req, RenderLane.Listener l) {
    return full.submit(req, l, true);
  }

  public boolean isIdle() {
    return preview.isIdle() && full.isIdle() && aux.isIdle();
  }

  @Override
  public void close() {
    preview.close();
    full.close();
    aux.close();
    if (resources != null) {
      try {
        resources.close();
      } catch (Exception e) {
        System.getLogger("rora.render")
            .log(System.Logger.Level.WARNING, "Error al cerrar el coordinador", e);
      }
    }
  }
}
