package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.app.session.ExportTarget.Collision;
import io.github.erenriquezp.rora.app.session.RenderFailure;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.SessionSource;
import io.github.erenriquezp.rora.app.session.StudioIo;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.io.DecodedImage;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Raíz de los ViewModels: abrir, exportar, deshacer, herramienta activa y estado. */
public final class StudioViewModel<F> implements AutoCloseable {

  /** Herramienta del riel izquierdo. */
  public enum Tool {
    ADJUST,
    FILTERS,
    CROP
  }

  private static final long SHOW_PROGRESS_AFTER_NANOS = 300_000_000L;

  private final Env<F> env;
  private final Messages m;
  private final GestureTracker gesture;
  private final ViewerViewModel<F> viewer;
  private final HistogramViewModel<F> histogram;
  private final BasicAdjustViewModel<F> basic;
  private final ParamPanelViewModel<F> params;
  private final StackViewModel<F> stack;
  private final CatalogViewModel<F> catalog;
  private final CropViewModel<F> crop;
  private final NotificationsViewModel<F> notifications;
  private final IoProgressViewModel<F> ioProgress;
  private final ExportDialogViewModel<F> exportDialog;

  private final ObjectProperty<Prompt> prompt = new SimpleObjectProperty<>();
  private final ObjectProperty<Tool> tool = new SimpleObjectProperty<>(Tool.ADJUST);
  private final BooleanProperty hasImage = new SimpleBooleanProperty(false);
  private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
  private final BooleanProperty canRedo = new SimpleBooleanProperty(false);
  private final StringProperty fileName = new SimpleStringProperty("");
  private final StringProperty status = new SimpleStringProperty("");
  private final StringProperty format = new SimpleStringProperty("");
  private final StringProperty size = new SimpleStringProperty("");

  private final EditSession.Listener<F> listener;
  private long loadGeneration;
  private long renderStartedAt;
  private String restingStatus;
  private CancellationToken loadToken = CancellationToken.create();
  private Img latestPixels;
  private Runnable afterExport;
  private Consumer<Path> revealer = p -> {};

  public StudioViewModel(Env<F> env, Function<Img, F> auxConverter, Function<Img, F> overlay) {
    this.env = env;
    this.m = env.messages();
    this.gesture = new GestureTracker(env.session(), env.scheduler(), env.publisher());
    this.notifications = new NotificationsViewModel<>(env);
    this.ioProgress = new IoProgressViewModel<>(env);
    this.exportDialog =
        new ExportDialogViewModel<>(
            env,
            new ExportDialogViewModel.Host() {
              @Override
              public Size outputSize() {
                return env.session().outputSize();
              }

              @Override
              public Optional<Img> previewPixels() {
                return Optional.ofNullable(latestPixels);
              }

              @Override
              public String sourceBaseName() {
                return fileName.get();
              }

              @Override
              public void export(
                  ExportSettings settings, Path folder, String name, Collision collision) {
                startExport(settings, folder, name, collision);
              }
            });
    this.viewer = new ViewerViewModel<>(env, auxConverter, this::renderProblem);
    this.histogram = new HistogramViewModel<>(env, overlay);
    this.stack = new StackViewModel<>(env);
    this.basic = new BasicAdjustViewModel<>(env, gesture);
    this.params = new ParamPanelViewModel<>(env, gesture);
    this.catalog = new CatalogViewModel<>(env, stack);
    this.crop = new CropViewModel<>(env, gesture, () -> tool.set(Tool.ADJUST));
    this.restingStatus = m.get("ui.status.start");
    this.status.set(restingStatus);

    stack.selectedProperty().addListener((obs, was, now) -> params.show(now.intValue()));
    tool.addListener(
        (obs, was, now) -> {
          if (now == Tool.ADJUST) {
            stack.clearSelection();
          }
          if (now == Tool.CROP) {
            crop.activate();
          } else if (was == Tool.CROP) {
            crop.cancel();
          }
        });

    this.listener =
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<F> frame) {
            onFrame(frame);
          }

          @Override
          public void historyChanged(int depth, boolean undoable, boolean redoable) {
            canUndo.set(undoable);
            canRedo.set(redoable);
            syncRecipe();
          }

          @Override
          public void failed(RenderFailure failure) {
            onRenderFailed(failure);
          }

          @Override
          public void progress(double fraction) {
            onProgress(fraction);
          }
        };
    env.session().addListener(listener);
  }

  public ViewerViewModel<F> viewer() {
    return viewer;
  }

  public HistogramViewModel<F> histogram() {
    return histogram;
  }

  public BasicAdjustViewModel<F> basic() {
    return basic;
  }

  public ParamPanelViewModel<F> params() {
    return params;
  }

  public StackViewModel<F> stack() {
    return stack;
  }

  public CatalogViewModel<F> catalog() {
    return catalog;
  }

  public CropViewModel<F> crop() {
    return crop;
  }

  public NotificationsViewModel<F> notifications() {
    return notifications;
  }

  public IoProgressViewModel<F> ioProgress() {
    return ioProgress;
  }

  public ExportDialogViewModel<F> exportDialog() {
    return exportDialog;
  }

  /** Pregunta pendiente para el usuario; {@code null} si no hay. */
  public ObjectProperty<Prompt> promptProperty() {
    return prompt;
  }

  /** Cómo enseñar un archivo en su carpeta (lo aporta la vista). */
  public void setRevealer(Consumer<Path> value) {
    revealer = value;
  }

  public Messages messages() {
    return m;
  }

  public ObjectProperty<Tool> toolProperty() {
    return tool;
  }

  public BooleanProperty hasImageProperty() {
    return hasImage;
  }

  public BooleanProperty canUndoProperty() {
    return canUndo;
  }

  public BooleanProperty canRedoProperty() {
    return canRedo;
  }

  public StringProperty fileNameProperty() {
    return fileName;
  }

  public StringProperty statusProperty() {
    return status;
  }

  public StringProperty formatProperty() {
    return format;
  }

  public StringProperty sizeProperty() {
    return size;
  }

  private void syncRecipe() {
    var recipe = env.session().recipe();
    stack.sync(recipe);
    basic.sync(recipe);
    params.show(stack.selectedProperty().get());
    crop.sync(recipe);
    markRenderStart();
  }

  private void onFrame(EditSession.Frame<F> frame) {
    latestPixels = frame.pixels();
    viewer.frameReady(frame);
    histogram.accept(frame);
    size.set(m.format("ui.status.size", frame.fullWidth(), frame.fullHeight()));
    status.set(restingStatus);
  }

  private void onProgress(double fraction) {
    if (env.scheduler().nanoTime() - renderStartedAt > SHOW_PROGRESS_AFTER_NANOS) {
      status.set(m.format("ui.status.processing", Math.round(fraction * 100)));
    }
  }

  private void markRenderStart() {
    renderStartedAt = env.scheduler().nanoTime();
  }

  private void onRenderFailed(RenderFailure failure) {
    if (failure.kind() == RenderFailure.Kind.OUT_OF_MEMORY) {
      notifications.error(m.get("ui.error.memory"), failure.message());
    } else {
      renderProblem(failure.message());
    }
  }

  private void renderProblem(String details) {
    notifications.error(m.get("ui.error.render"), details);
  }

  private void setStatus(String text) {
    restingStatus = text;
    status.set(text);
  }

  /** Abre {@code file}, avisando antes si hay cambios sin exportar. */
  public void requestOpen(File file) {
    guarded(() -> open(file));
  }

  /**
   * Antes de cerrar la ventana.
   *
   * @return {@code true} si se puede cerrar ya; si no, {@code closer} se llamará tras preguntar
   */
  public boolean requestClose(Runnable closer) {
    if (!env.session().isModified()) {
      return true;
    }
    guarded(closer);
    return false;
  }

  private void guarded(Runnable action) {
    if (!env.session().isModified()) {
      action.run();
      return;
    }
    prompt.set(
        new Prompt(
            m.get("ui.unsaved.title"),
            m.format("ui.unsaved.body", fileName.get()),
            List.of(
                new Prompt.Choice(
                    m.get("ui.unsaved.export"),
                    true,
                    () -> {
                      prompt.set(null);
                      afterExport = action;
                      showExport();
                    }),
                new Prompt.Choice(
                    m.get("ui.unsaved.discard"),
                    false,
                    () -> {
                      prompt.set(null);
                      action.run();
                    }),
                new Prompt.Choice(m.get("ui.action.cancel"), false, () -> prompt.set(null)))));
  }

  /** Abre {@code file} fuera del hilo de la interfaz; gana la última petición. */
  public void open(File file) {
    if (file == null) {
      return;
    }
    long generation = ++loadGeneration;
    loadToken.cancel();
    CancellationToken token = CancellationToken.create();
    loadToken = token;
    setStatus(m.format("ui.status.opening", file.getName()));
    ioProgress.begin(m.format("ui.status.opening", file.getName()), token::cancel);
    long retained = env.session().source().map(s -> s.image().pixelCount() * 4L).orElse(0L);
    run(
        () -> StudioIo.open(file.toPath(), retained, token, progressPublisher()),
        opened -> {
          if (generation == loadGeneration) {
            ioProgress.end();
            install(opened.source(), file.getName(), opened.decoded().info().format().name());
            showNotes(opened.decoded());
          }
        },
        error -> {
          if (generation == loadGeneration) {
            ioProgress.end();
            setStatus(m.get("ui.status.ready"));
            if (!(error instanceof ImagingException.Cancelled)) {
              problem(m.format("ui.error.open", file.getName()), error);
            }
          }
        });
  }

  private void showNotes(DecodedImage decoded) {
    for (DecodedImage.Note n : decoded.notes()) {
      notifications.warning(m.format(n.key(), n.args().toArray()));
    }
  }

  private void problem(String title, Exception error) {
    if (error instanceof ImagingException e) {
      notifications.error(
          m.format("ui.error.titled", title, m.format(e.messageKey(), e.args().toArray())),
          e.technical());
    } else {
      notifications.error(title, String.valueOf(error.getMessage()));
    }
  }

  private Progress progressPublisher() {
    int[] last = {-1};
    return (done, total) -> {
      int percent = total <= 0 ? 0 : (int) (done * 100 / total);
      if (percent != last[0]) {
        last[0] = percent;
        env.publisher().execute(() -> ioProgress.update(percent / 100.0));
      }
    };
  }

  /** Lleva al editor una imagen creada en el Laboratorio. */
  public void useImage(BufferedImage image) {
    guarded(
        () -> {
          long generation = ++loadGeneration;
          run(
              () -> SessionSource.of(image),
              source -> {
                if (generation == loadGeneration) {
                  install(source, m.get("ui.lab.untitled"), "");
                }
              },
              error -> notifications.error(m.get("ui.error.render"), error.getMessage()));
        });
  }

  /** El usuario pidió {@code --lab} pero el módulo no está disponible. */
  public void labMissing() {
    notifications.error(m.get("ui.lab.missing"), "");
  }

  private void install(SessionSource source, String name, String label) {
    if (tool.get() == Tool.CROP) {
      tool.set(Tool.ADJUST);
    }
    viewer.clear();
    histogram.clear();
    stack.clearSelection();
    viewer.pushViewport();
    latestPixels = null;
    env.session().load(source);
    hasImage.set(true);
    fileName.set(name);
    format.set(label);
    setStatus(m.format("ui.status.opened", name));
  }

  /** Abre el diálogo de exportación. */
  public void showExport() {
    if (env.session().hasImage()) {
      String folder =
          env.session()
              .source()
              .flatMap(SessionSource::origin)
              .map(Path::getParent)
              .map(Path::toString)
              .orElse("");
      exportDialog.show(folder);
    }
  }

  /**
   * Exporta a resolución completa; la receta queda fijada en el momento de pedirlo. Si el destino
   * existe, pregunta antes de hacer nada.
   */
  public void startExport(ExportSettings settings, Path folder, String name, Collision collision) {
    if (!env.session().hasImage()) {
      return;
    }
    Optional<Path> original = env.session().source().flatMap(SessionSource::origin);
    run(
        () -> StudioIo.targetState(folder.resolve(name), original),
        state -> {
          ExportTarget base = new ExportTarget(folder, name, collision, false, false, original);
          if (state == StudioIo.TargetState.FREE || collision == Collision.NUMBER) {
            render(settings, base);
          } else {
            askOverwrite(settings, base, state == StudioIo.TargetState.ORIGINAL);
          }
        },
        error -> exportFailed(name, error));
  }

  private void askOverwrite(ExportSettings settings, ExportTarget t, boolean isOriginal) {
    Runnable overwrite =
        () -> {
          prompt.set(null);
          if (isOriginal) {
            askOverwriteOriginal(settings, t);
          } else {
            render(settings, confirmed(t, false));
          }
        };
    Runnable number =
        () -> {
          prompt.set(null);
          render(
              settings,
              new ExportTarget(
                  t.folder(), t.fileName(), Collision.NUMBER, false, false, t.original()));
        };
    prompt.set(
        new Prompt(
            m.get("ui.collision.title"),
            m.format("ui.collision.body", t.fileName()),
            List.of(
                new Prompt.Choice(m.get("ui.collision.number"), true, number),
                new Prompt.Choice(m.get("ui.collision.overwrite"), false, overwrite),
                new Prompt.Choice(m.get("ui.action.cancel"), false, this::cancelPrompt))));
  }

  private void askOverwriteOriginal(ExportSettings settings, ExportTarget t) {
    prompt.set(
        new Prompt(
            m.get("ui.original.title"),
            m.format("ui.original.body", t.fileName()),
            List.of(
                new Prompt.Choice(
                    m.get("ui.original.confirm"),
                    false,
                    () -> {
                      prompt.set(null);
                      render(settings, confirmed(t, true));
                    }),
                new Prompt.Choice(m.get("ui.action.cancel"), true, this::cancelPrompt))));
  }

  private void cancelPrompt() {
    prompt.set(null);
    afterExport = null;
  }

  private static ExportTarget confirmed(ExportTarget t, boolean original) {
    return new ExportTarget(
        t.folder(), t.fileName(), Collision.OVERWRITE, true, original, t.original());
  }

  private void render(ExportSettings settings, ExportTarget target) {
    ImageMetadata metadata =
        env.session().source().map(SessionSource::metadata).orElse(ImageMetadata.EMPTY);
    CancellationToken token = CancellationToken.create();
    String name = target.fileName();
    setStatus(m.format("ui.status.exporting", name));
    RenderLane.Ticket[] ticket = {null};
    ioProgress.begin(
        m.format("ui.status.exporting", name),
        () -> {
          token.cancel();
          if (ticket[0] != null) {
            ticket[0].cancel();
          }
          afterExport = null;
          setStatus(m.get("ui.status.ready"));
        });
    ticket[0] =
        env.session()
            .exportImg(
                image ->
                    run(
                        () ->
                            StudioIo.export(
                                image, metadata, settings, target, token, progressPublisher()),
                        this::exported,
                        error -> exportFailed(name, error)),
                failure -> {
                  ioProgress.end();
                  setStatus(m.get("ui.status.ready"));
                  notifications.error(m.format("ui.error.export", name), failure.message());
                });
  }

  private void exported(StudioIo.Exported e) {
    ioProgress.end();
    env.session().markExported();
    String name = e.file().getFileName().toString();
    setStatus(m.format("ui.status.exported", name));
    notifications.info(
        m.format("ui.status.exported", name),
        new NotificationsViewModel.Action(
            m.get("ui.action.reveal"), () -> revealer.accept(e.file())));
    Runnable next = afterExport;
    afterExport = null;
    if (next != null) {
      next.run();
    }
  }

  private void exportFailed(String name, Exception error) {
    ioProgress.end();
    setStatus(m.get("ui.status.ready"));
    afterExport = null;
    if (!(error instanceof ImagingException.Cancelled)) {
      problem(m.format("ui.error.export", name), error);
    }
  }

  private <T> void run(Callable<T> work, Consumer<T> onDone, Consumer<Exception> onFailed) {
    env.io()
        .execute(
            () -> {
              try {
                T value = work.call();
                env.publisher().execute(() -> onDone.accept(value));
              } catch (Exception e) {
                env.publisher().execute(() -> onFailed.accept(e));
              }
            });
  }

  public void undo() {
    gesture.end();
    env.session().undo();
  }

  public void redo() {
    gesture.end();
    env.session().redo();
  }

  /** Quita todos los ajustes; se puede deshacer. */
  public void reset() {
    if (env.session().hasImage() && env.session().recipe().size() > 0) {
      gesture.end();
      stack.clearSelection();
      env.session().resetRecipe();
      notifications.info(m.get("ui.status.reset"));
    }
  }

  @Override
  public void close() {
    env.session().removeListener(listener);
    histogram.close();
  }
}
