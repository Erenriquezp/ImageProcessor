package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.fx.FxImages;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.app.session.StallMonitor;
import io.github.erenriquezp.rora.app.theme.Theme;
import io.github.erenriquezp.rora.app.viewmodel.Env;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Aplicación con la interfaz nueva; se lanza con {@code --ui=next}. */
public class StudioApp extends Application {

  private static final System.Logger LOG = System.getLogger("rora.app");
  private static final String APP_ICON = "/io/github/erenriquezp/rora/app-icon.png";

  private final ExecutorService background =
      RenderCoordinator.singleThread("rora-histogram", Thread.NORM_PRIORITY);
  private final ExecutorService io =
      RenderCoordinator.singleThread("rora-io", Thread.NORM_PRIORITY);
  private final FramePool<WritableImage> afterPool = FxImages.newPool();
  private final FramePool<WritableImage> beforePool = FxImages.newPool();
  private final FramePool<WritableImage> overlayPool = FxImages.newPool();
  private EditSession<WritableImage> session;
  private StudioViewModel<WritableImage> viewModel;
  private MainWindow window;
  private StallMonitor stallMonitor;

  /** Ventana propia solo en Windows; en macOS y Linux, la decoración nativa. */
  static boolean usesCustomChrome() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  /** Almacén de disposición; las capturas usan uno en memoria. */
  protected LayoutStore layoutStore() {
    return LayoutStore.preferences();
  }

  /** ViewModel raíz, para la herramienta de capturas. */
  protected final StudioViewModel<WritableImage> viewModel() {
    return viewModel;
  }

  @Override
  public void start(Stage stage) {
    Theme.install();
    Messages messages = Messages.of(Locale.getDefault());
    FxImages.Format format = FxImages.configuredFormat();
    session = EditSession.create(Platform::runLater, img -> FxImages.toFx(img, afterPool, format), true);
    viewModel =
        new StudioViewModel<>(
            new Env<>(
                session, messages, Scheduler.system(), Platform::runLater, background, io),
            img -> FxImages.toFx(img, beforePool, format),
            img -> FxImages.toFx(img, overlayPool, format));

    if (usesCustomChrome()) {
      stage.initStyle(StageStyle.UNDECORATED);
    }
    stage.setTitle(messages.get("ui.app.title"));
    var icon = getClass().getResourceAsStream(APP_ICON);
    if (icon != null) {
      stage.getIcons().add(new Image(icon));
    }
    window = new MainWindow(viewModel, stage, layoutStore());
    Scene scene = window.build(afterPool, beforePool, overlayPool);
    Theme.apply(scene);
    stage.setScene(scene);
    for (FramePool<WritableImage> pool : List.of(afterPool, beforePool, overlayPool)) {
      FxImages.bindPulses(scene.getRoot(), pool);
    }
    startStallMonitorIfEnabled();
    stage.setOnHidden(e -> window.saveLayout());
    stage.show();
  }

  private void startStallMonitorIfEnabled() {
    if (!Boolean.getBoolean("rora.fxStallMonitor")) {
      return;
    }
    Thread fx = Thread.currentThread();
    stallMonitor =
        new StallMonitor(
            Scheduler.system(),
            Platform::runLater,
            () -> {
              StringBuilder sb = new StringBuilder();
              for (StackTraceElement frame : fx.getStackTrace()) {
                sb.append("    at ").append(frame).append('\n');
              }
              return sb.toString();
            },
            message -> LOG.log(System.Logger.Level.WARNING, message),
            StallMonitor.DEFAULT_TICK_NANOS,
            StallMonitor.DEFAULT_THRESHOLD_NANOS);
    stallMonitor.start();
  }

  @Override
  public void stop() {
    if (stallMonitor != null) {
      stallMonitor.close();
    }
    if (window != null) {
      window.close();
    }
    viewModel.close();
    session.close();
    background.shutdownNow();
    io.shutdownNow();
  }
}
