package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ImageIo;
import io.github.erenriquezp.rora.app.session.RenderFailure;
import io.github.erenriquezp.rora.app.session.SessionSource;
import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
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

  public StudioViewModel(Env<F> env, Function<Img, F> auxConverter, Function<Img, F> overlay) {
    this.env = env;
    this.m = env.messages();
    this.gesture = new GestureTracker(env.session(), env.scheduler(), env.publisher());
    this.notifications = new NotificationsViewModel<>(env);
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

  /** Abre {@code file} fuera del hilo de la interfaz; gana la última petición. */
  public void open(File file) {
    if (file == null) {
      return;
    }
    long generation = ++loadGeneration;
    setStatus(m.format("ui.status.opening", file.getName()));
    run(
        () -> ImageIo.load(file),
        source -> {
          if (generation == loadGeneration) {
            install(source, file.getName(), extensionOf(file));
          }
        },
        error -> {
          if (generation == loadGeneration) {
            setStatus(m.get("ui.status.ready"));
            notifications.error(
                m.format("ui.error.open", file.getName()),
                m.get("ui.error.open.hint") + "\n" + error.getMessage());
          }
        });
  }

  /** Lleva al editor una imagen creada en el Laboratorio. */
  public void useImage(BufferedImage image) {
    long generation = ++loadGeneration;
    run(
        () -> SessionSource.of(image),
        source -> {
          if (generation == loadGeneration) {
            install(source, m.get("ui.lab.untitled"), "");
          }
        },
        error -> notifications.error(m.get("ui.error.render"), error.getMessage()));
  }

  /** El usuario pidió {@code --lab} pero el módulo no está disponible. */
  public void labMissing() {
    notifications.error(m.get("ui.lab.missing"), "");
  }

  private void install(SessionSource source, String name, String extension) {
    if (tool.get() == Tool.CROP) {
      tool.set(Tool.ADJUST);
    }
    viewer.clear();
    histogram.clear();
    stack.clearSelection();
    viewer.pushViewport();
    env.session().load(source);
    hasImage.set(true);
    fileName.set(name);
    format.set(extension);
    setStatus(m.format("ui.status.opened", name));
  }

  private static String extensionOf(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot >= 0 && dot < name.length() - 1
        ? name.substring(dot + 1).toUpperCase(Locale.ROOT)
        : "";
  }

  /** Destino de exportación: siempre PNG mientras no llegue la exportación avanzada. */
  public static File exportTarget(File chosen) {
    String name = chosen.getName();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return new File(chosen.getParentFile(), base + ".png");
  }

  /** Exporta a resolución completa; la receta queda fijada en el momento de pedirlo. */
  public void export(File chosen) {
    if (chosen == null || !env.session().hasImage()) {
      return;
    }
    File file = exportTarget(chosen);
    setStatus(m.format("ui.status.exporting", file.getName()));
    env.session()
        .export(
            image ->
                run(
                    () -> {
                      ImageIo.save(image, file);
                      return file;
                    },
                    f -> {
                      setStatus(m.format("ui.status.exported", f.getName()));
                      notifications.info(m.format("ui.status.exported", f.getName()));
                    },
                    error -> exportFailed(file, error.getMessage())),
            failure -> exportFailed(file, failure.message()));
  }

  private void exportFailed(File file, String details) {
    setStatus(m.get("ui.status.ready"));
    notifications.error(m.format("ui.error.export", file.getName()), details);
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
