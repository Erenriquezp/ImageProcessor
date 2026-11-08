package io.github.erenriquezp.rora.app.ui;

import io.github.erenriquezp.rora.app.fx.FxImages;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.app.session.LatestWins;
import io.github.erenriquezp.rora.app.session.PaneWork;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

/**
 * Trabajo "último gana" de un panel, fuera del hilo FX: calcula la imagen exacta y prepara la copia
 * para mostrar (como mucho {@link PaneWork#DISPLAY_MAX_PIXELS}, ver {@code SessionMemoryTest}).
 */
final class PaneImageWorker implements AutoCloseable {

  /** Imagen exacta y su copia lista para el {@code ImageView}. */
  record Shown(BufferedImage exact, WritableImage fx) {}

  private final ExecutorService executor;
  private final FramePool<WritableImage> pool = FxImages.newPool();
  private final ImageView view;
  private final LatestWins<Supplier<BufferedImage>, Shown> work;
  private final LatestWins<File, BufferedImage> reader;

  PaneImageWorker(String name, ImageView view) {
    this.view = view;
    this.executor = RenderCoordinator.singleThread("rora-pane-" + name, Thread.NORM_PRIORITY);
    this.work =
        new LatestWins<>(
            name,
            executor,
            Platform::runLater,
            (job, token) -> {
              BufferedImage exact = job.get();
              token.throwIfCancelled();
              Img copy = PaneWork.displayCopy(exact, PaneWork.DISPLAY_MAX_PIXELS, token);
              return new Shown(exact, FxImages.toFx(copy, pool, FxImages.configuredFormat()));
            });
    this.reader =
        new LatestWins<>(
            name + "-read", executor, Platform::runLater, (file, token) -> PaneWork.read(file));
    FxImages.bindPulses(view, pool);
  }

  /** Calcula {@code job} en segundo plano; solo la última petición llega a {@code onShown}. */
  void submit(Supplier<BufferedImage> job, Consumer<Shown> onShown, Consumer<Throwable> onError) {
    work.submit(job, onShown, onError);
  }

  /** Lee un archivo en segundo plano. */
  void read(File file, Consumer<BufferedImage> onImage, Consumer<Throwable> onError) {
    reader.submit(file, onImage, onError);
  }

  /** (FX) Pone el resultado en el {@code ImageView}. */
  void show(Shown shown) {
    view.setImage(shown.fx());
    pool.displayed(shown.fx());
  }

  /** (FX) Abandona lo pendiente y vacía la vista. */
  void clear() {
    work.cancelAll();
    view.setImage(null);
  }

  @Override
  public void close() {
    work.close();
    reader.close();
    executor.shutdownNow();
  }
}
