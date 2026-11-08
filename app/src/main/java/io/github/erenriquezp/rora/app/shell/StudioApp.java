package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.fx.FxImages;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.lab.LabBundle;
import io.github.erenriquezp.rora.app.lab.LabBundles;
import io.github.erenriquezp.rora.app.lab.LabContext;
import io.github.erenriquezp.rora.app.lab.LabTool;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderFailure;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.app.session.StallMonitor;
import io.github.erenriquezp.rora.app.theme.Theme;
import io.github.erenriquezp.rora.app.viewmodel.Env;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Aplicación principal. Con {@code --lab} añade el Laboratorio, si su módulo está disponible. */
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

  /** Argumentos de la línea de órdenes; el humo arranca la aplicación sin {@code launch}. */
  protected List<String> arguments() {
    return getParameters().getRaw();
  }

  /** ViewModel raíz, para la herramienta de capturas. */
  protected final StudioViewModel<WritableImage> viewModel() {
    return viewModel;
  }

  @Override
  public void start(Stage stage) {
    Theme.install();
    boolean labRequested = labRequested(arguments());
    List<LabBundle> labs = labRequested ? LabBundles.discover() : List.of();
    List<ResourceBundle> labLabels = new ArrayList<>();
    List<Catalog.Entry> labEntries = new ArrayList<>();
    List<LabTool> tools = new ArrayList<>();
    for (LabBundle lab : labs) {
      labLabels.add(lab.labels(Locale.getDefault()));
      labEntries.addAll(lab.catalogEntries());
      tools.addAll(lab.tools());
    }
    Messages messages = Messages.of(Locale.getDefault(), labLabels);
    FxImages.Format format = FxImages.configuredFormat();
    session =
        EditSession.create(Platform::runLater, img -> FxImages.toFx(img, afterPool, format), true);
    viewModel =
        new StudioViewModel<>(
            new Env<>(
                session,
                messages,
                Scheduler.system(),
                Platform::runLater,
                background,
                io,
                labEntries),
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
    Runnable openLab =
        tools.isEmpty() ? null : () -> LabWindow.open(stage, tools, labContext(stage));
    window = new MainWindow(viewModel, stage, layoutStore(), openLab);
    Scene scene = window.build(afterPool, beforePool, overlayPool);
    Theme.apply(scene);
    stage.setScene(scene);
    for (FramePool<WritableImage> pool : List.of(afterPool, beforePool, overlayPool)) {
      FxImages.bindPulses(scene.getRoot(), pool);
    }
    startStallMonitorIfEnabled();
    stage.setOnHidden(e -> window.saveLayout());
    stage.show();
    if (labRequested && labs.isEmpty()) {
      viewModel.labMissing();
    }
  }

  /** La bandera {@code --lab} activa el Laboratorio si su módulo está disponible. */
  static boolean labRequested(List<String> args) {
    return args.contains("--lab");
  }

  private LabContext labContext(Stage stage) {
    return new LabContext() {
      @Override
      public javafx.stage.Window owner() {
        return stage;
      }

      @Override
      public boolean hasImage() {
        return session.hasImage();
      }

      @Override
      public void currentImage(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed) {
        session.withCurrentImage(onImage, onFailed);
      }

      @Override
      public void useImage(BufferedImage image) {
        viewModel.useImage(image);
      }

      @Override
      public void status(String message) {
        viewModel.statusProperty().set(message);
      }
    };
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
