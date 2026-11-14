package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.diag.AppDirs;
import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.diag.Diagnostics;
import io.github.erenriquezp.rora.app.diag.Notices;
import io.github.erenriquezp.rora.app.diag.Redactor;
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
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperWorkspace;
import io.github.erenriquezp.rora.app.theme.Theme;
import io.github.erenriquezp.rora.app.update.GitHubReleasesClient;
import io.github.erenriquezp.rora.app.update.UpdateChecker;
import io.github.erenriquezp.rora.app.viewmodel.Env;
import io.github.erenriquezp.rora.app.viewmodel.HelpViewModel;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel;
import io.github.erenriquezp.rora.wallpapers.preset.UserPresetStore;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Screen;
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
  private final ExecutorService helpWorker =
      RenderCoordinator.singleThread("rora-help", Thread.NORM_PRIORITY);
  private final ExecutorService updateWorker =
      RenderCoordinator.singleThread("rora-update", Thread.NORM_PRIORITY);
  private final FramePool<WritableImage> afterPool = FxImages.newPool();
  private final FramePool<WritableImage> beforePool = FxImages.newPool();
  private final FramePool<WritableImage> overlayPool = FxImages.newPool();
  private EditSession<WritableImage> session;
  private StudioViewModel<WritableImage> viewModel;
  private MainWindow window;
  private StallMonitor stallMonitor;
  private HelpViewModel help;

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
            img -> FxImages.toFx(img, overlayPool, format),
            img -> {
              WritableImage w = new WritableImage(img.width(), img.height());
              FxImages.write(img, w, format);
              return w;
            },
            WallpaperWorkspace.withBundledDetector(),
            new UserPresetStore(
                UserPresetStore.defaultDirectory(
                    System.getenv(),
                    System.getProperty("os.name", ""),
                    System.getProperty("user.home", "."))));

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
    help = helpViewModel(messages);
    Diagnostics.errors()
        .ifPresent(
            g ->
                g.attach(
                    help,
                    Platform::runLater,
                    () -> {
                      session.releaseMemory();
                      Platform.runLater(
                          () -> {
                            viewModel.enhance().cancel();
                            viewModel.wallpapers().cancelExport();
                          });
                    }));
    window =
        new MainWindow(viewModel, stage, layoutStore(), openLab, help, updateViewModel(messages));
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
    // Solo al arrancar por Launcher: las herramientas de desarrollo pasan sus propios argumentos.
    if (Diagnostics.errors().isPresent()) {
      help.checkPreviousCrash();
      fileArgument(arguments()).ifPresent(viewModel::open);
    }
  }

  /**
   * Foto pasada por la línea de órdenes o arrastrada sobre el icono (Windows la pasa como
   * argumento): el primer argumento que no es una opción y es un archivo.
   */
  static Optional<File> fileArgument(List<String> args) {
    for (String a : args) {
      if (a == null || a.isBlank() || a.startsWith("--")) {
        continue;
      }
      File f = new File(a);
      if (f.isFile()) {
        return Optional.of(f);
      }
    }
    return Optional.empty();
  }

  /** Comprobación manual de actualizaciones; la única red de la app, y solo al pedirla. */
  protected UpdateViewModel updateViewModel(Messages messages) {
    AppInfo info = AppInfo.current();
    return new UpdateViewModel(
        messages,
        Platform::runLater,
        updateWorker,
        new UpdateChecker(
            new GitHubReleasesClient(info.userAgent()),
            UpdateChecker.enabledByProperty(System.getProperty("rora.updates"))),
        info,
        url -> getHostServices().showDocument(url));
  }

  /** ViewModel de ayuda; la herramienta de capturas lo usa para sus estados. */
  protected final HelpViewModel help() {
    return help;
  }

  private HelpViewModel helpViewModel(Messages messages) {
    boolean packaged = Diagnostics.packaged();
    Optional<Path> legal =
        packaged
            ? Optional.of(
                Path.of(System.getProperty("java.home"))
                    .toAbsolutePath()
                    .getParent()
                    .resolve("legal"))
            : Optional.empty();
    return new HelpViewModel(
        messages,
        Platform::runLater,
        helpWorker,
        viewModel.notifications(),
        viewModel.promptProperty(),
        new HelpViewModel.Context(
            AppInfo.current(),
            AppDirs.fromSystem(),
            new Redactor(Redactor.Context.fromSystem()),
            Diagnostics.logs(),
            Diagnostics.errors(),
            packaged,
            legal,
            ZonedDateTime::now),
        new HelpViewModel.Host() {
          @Override
          public void copyToClipboard(String text) {
            ClipboardContent c = new ClipboardContent();
            c.putString(text);
            Clipboard.getSystemClipboard().setContent(c);
          }

          @Override
          public void openFolder(Path dir) {
            StudioApp.openFolder(dir);
          }

          @Override
          public void browse(String url) {
            getHostServices().showDocument(url);
          }

          @Override
          public HelpViewModel.UiFacts uiFacts() {
            return StudioApp.this.uiFacts(messages);
          }
        });
  }

  private HelpViewModel.UiFacts uiFacts(Messages messages) {
    List<String> screens = new ArrayList<>();
    for (Screen s : Screen.getScreens()) {
      screens.add(
          String.format(
              Locale.ROOT,
              "%.0f×%.0f lógicos, escala %.0f %%",
              s.getBounds().getWidth(),
              s.getBounds().getHeight(),
              s.getOutputScaleX() * 100));
    }
    String size = viewModel.sizeProperty().get();
    String fmt = viewModel.formatProperty().get();
    Optional<String> image =
        session.hasImage() ? Optional.of((size + " " + fmt).strip()) : Optional.empty();
    String ai =
        session
            .ai()
            .map(
                a ->
                    a.modelId()
                        + " "
                        + a.modelVersion()
                        + ", "
                        + a.threads()
                        + " hilos (el motor se carga al usar Mejorar o Wallpapers)")
            .orElse("no incluido");
    return new HelpViewModel.UiFacts(
        messages.locale().toLanguageTag()
            + " (sistema "
            + Locale.getDefault().toLanguageTag()
            + ")",
        Notices.javafxVersion().orElse("?"),
        Optional.of(Platform.isSupported(ConditionalFeature.SCENE3D)),
        screens,
        image,
        session.activeStepIds(),
        ai,
        session.cacheBytes());
  }

  /** Abre una carpeta en el explorador, fuera del hilo de la interfaz. */
  static void openFolder(Path dir) {
    Thread t =
        new Thread(
            () -> {
              try {
                if (usesCustomChrome()) {
                  new ProcessBuilder("explorer.exe", dir.toString()).start();
                } else if (Desktop.isDesktopSupported()) {
                  Desktop.getDesktop().open(dir.toFile());
                }
              } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "No se pudo abrir la carpeta " + dir, e);
              }
            },
            "rora-open-folder");
    t.setDaemon(true);
    t.start();
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
    helpWorker.shutdownNow();
    updateWorker.shutdownNow();
  }
}
